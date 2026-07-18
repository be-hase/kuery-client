package dev.hsbrysk.kuery.compiler.fir

import dev.hsbrysk.kuery.compiler.SqlSyntaxCheck
import dev.hsbrysk.kuery.compiler.misc.CallableIds
import dev.hsbrysk.kuery.compiler.misc.ClassIds
import net.sf.jsqlparser.JSQLParserException
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.parser.ParseException
import net.sf.jsqlparser.parser.TokenMgrException
import net.sf.jsqlparser.parser.feature.FeatureConfiguration
import net.sf.jsqlparser.statement.Statement
import net.sf.jsqlparser.util.validation.Validation
import net.sf.jsqlparser.util.validation.feature.DatabaseType
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.expressions.FirAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirLazyBlock
import org.jetbrains.kotlin.fir.expressions.FirReturnExpression
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.FirThisReceiverExpression
import org.jetbrains.kotlin.fir.expressions.unwrapArgument
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.lookupSuperTypes
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeDefinitelyNotNullType
import org.jetbrains.kotlin.fir.types.ConeIntersectionType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Validates the SQL syntax of a `sql { ... }` / `Sql { ... }` block at compile time, only when
 * the complete statement is statically known: every statement of the block must be an
 * add/unaryPlus call on the block's own builder whose text [SqlTextReconstruction] can
 * reconstruct, or a call to a static same-module helper that is inlined (see
 * [inlinedHelperTextOrNull]). Blocks containing anything else (conditionals, dynamic or
 * compiled helpers, addUnsafe, variables, calls on another builder, ...) are silently skipped —
 * a dynamically assembled statement cannot be reconstructed, and guessing would produce false
 * positives.
 *
 * The reconstructed texts are joined with newlines exactly like `DefaultSqlBuilder.addUnsafe`
 * accumulates them, then parsed with JSqlParser. A parse failure is reported as the
 * [KueryClientDiagnostics.KUERY_SQL_SYNTAX] warning, anchored to the add call the failing line
 * belongs to. JSqlParser is deliberately lenient, so this check trades detection power for a
 * low false-positive rate; internal parser errors (including its parse timeout) fail open.
 *
 * Unless [mode] is [SqlSyntaxCheck.GENERIC], a statement that parses is additionally checked
 * against that dialect's feature allow-list (JSqlParser's validation framework) and violations
 * are reported as [KueryClientDiagnostics.KUERY_SQL_DIALECT] — e.g. `ON DUPLICATE KEY UPDATE`
 * under `postgresql`. This is feature-level, not a full dialect grammar: some cross-dialect
 * syntax still passes, which keeps the failure mode on the false-negative side.
 */
internal class SqlSyntaxChecker(
    private val autoTrimIndent: Boolean,
    mode: SqlSyntaxCheck,
) : FirFunctionCallChecker(MppCheckerKind.Common) {
    // null for GENERIC: parse only, no feature validation.
    private val dialect: DatabaseType? = mode.toDatabaseTypeOrNull()

    // The user-facing name of the mode, i.e. the option vocabulary ("ansi", not "ansi_sql").
    private val dialectLabel: String = mode.optionValue
    private class Part(
        val text: String,
        val source: KtSourceElement?,
    )

    private val entryPoints = SqlBlockEntryPoints()

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        val block = entryPoints.sqlBlockOrNull(expression, context.session) ?: return
        val parts = reconstructedPartsFailSafe(block) ?: return
        val joined = parts.joinToString("\n") { it.text }
        // DefaultSqlBuilder.build() trims the assembled body — including unicode whitespace the
        // parser would reject — so mirror it, and remember how many fully removed leading lines
        // to add back when mapping parser positions to parts. lines() counts CRLF, CR and LF as
        // one break each, matching the parser's line numbering.
        val sql = joined.trim()
        if (sql.isEmpty()) return
        val leadingLineOffset = joined
            .take(joined.indexOfFirst { !it.isWhitespace() })
            .lines()
            .size - 1
        reportFailures(expression, parts, sql, leadingLineOffset)
    }

    // A bug in the reconstruction must never escape the checker: it would fail the user's whole
    // compilation with an internal error. Unexpected failures skip the block, like the parser's
    // own fail-open path.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun reconstructedPartsFailSafe(block: FirAnonymousFunction): List<Part>? = try {
        reconstructedPartsOrNull(block)
    } catch (e: Exception) {
        null
    }

    // Which calls start a checkable `sql { }` block, and the receiver-type matching
    // behind it (kept in its own class to stay within the function-count threshold).
    // Matching by receiver type deliberately includes `sql`-named overloads a client subtype
    // declares itself (as long as they take a SqlBuilder-receiver lambda): such a lambda builds
    // SQL the same way, so checking it is acceptable — and usually useful.
    private class SqlBlockEntryPoints {
        // The client-type answer depends only on the receiver's ClassId; cache it per checker
        // instance (one exists per FirSession). Concurrent for safety under parallel checker runs.
        private val clientTypeCache = ConcurrentHashMap<ClassId, Boolean>()

        fun sqlBlockOrNull(
            call: FirFunctionCall,
            session: FirSession,
        ): FirAnonymousFunction? {
            // This checker runs on every function call in the project, so pre-filter on the
            // callee name before any symbol resolution.
            val isEntryPoint = when (call.calleeReference.name) {
                CallableIds.SQL_TOP_LEVEL.callableName -> call.isTopLevelSqlCall()
                SQL_MEMBER_NAME -> call.isClientSqlCall(session)
                // An import alias can rename the top-level Sql entry point (members cannot be
                // aliased). The call is already resolved, so reading its callable id is cheap;
                // gate only on a lambda argument being present.
                else -> call.hasLambdaArgument() && call.isTopLevelSqlCall()
            }
            if (!isEntryPoint) return null
            return call.argumentList.arguments
                .asSequence()
                .map { it.unwrapArgument() }
                .filterIsInstance<FirAnonymousFunctionExpression>()
                .singleOrNull()
                ?.anonymousFunction
                // The lambda must actually be a SqlBuilder-receiver block: a like-named member on a
                // client subtype taking a plain lambda has different semantics and is not checked.
                ?.takeIf { it.isSqlBuilderLambda(session) }
        }

        private fun FirFunctionCall.isTopLevelSqlCall(): Boolean =
            calleeReference.toResolvedCallableSymbol()?.callableId == CallableIds.SQL_TOP_LEVEL

        private fun FirFunctionCall.hasLambdaArgument(): Boolean =
            argumentList.arguments.any { it.unwrapArgument() is FirAnonymousFunctionExpression }

        private fun FirAnonymousFunction.isSqlBuilderLambda(session: FirSession): Boolean = receiverParameter
            ?.typeRef
            ?.coneType
            ?.fullyExpandedType(session)
            ?.classId == ClassIds.SQL_BUILDER

        // Matching by the receiver's type rather than the resolved callable covers both
        // sub-interfaces (fake overrides) and overriding decorators such as
        // `class Decorated(d: KueryClient) : KueryClient by d`, mirroring the IR-side override
        // walk in SqlIdInjectionTransformer.
        private fun FirFunctionCall.isClientSqlCall(session: FirSession): Boolean {
            val receiverType = dispatchReceiver?.resolvedType ?: return false
            return receiverType.isClientType(session, visitedTypeParameters = mutableSetOf())
        }

        // Also recognizes generic receivers (`fun <T : KueryClient> ...`) via type-parameter
        // upper bounds, and the intersection / definitely-non-null types smart casts produce.
        // The visited set guards against (illegal but representable) cyclic bounds.
        private fun ConeKotlinType.isClientType(
            session: FirSession,
            visitedTypeParameters: MutableSet<FirTypeParameterSymbol>,
        ): Boolean = when (val expanded = fullyExpandedType(session)) {
            is ConeDefinitelyNotNullType -> expanded.original.isClientType(session, visitedTypeParameters)
            is ConeIntersectionType ->
                expanded.intersectedTypes.any { it.isClientType(session, visitedTypeParameters) }
            is ConeTypeParameterType -> {
                val symbol = expanded.lookupTag.typeParameterSymbol
                visitedTypeParameters.add(symbol) &&
                    symbol.resolvedBounds.any { it.coneType.isClientType(session, visitedTypeParameters) }
            }
            else -> {
                val classSymbol = expanded.toRegularClassSymbol(session)
                classSymbol != null &&
                    clientTypeCache.computeIfAbsent(classSymbol.classId) { classId ->
                        classId in ClassIds.SQL_CLIENTS ||
                            lookupSuperTypes(
                                classSymbol,
                                lookupInterfaces = true,
                                deep = true,
                                useSiteSession = session,
                            )
                                .any { it.classId in ClassIds.SQL_CLIENTS }
                    }
            }
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun reportFailures(
        expression: FirFunctionCall,
        parts: List<Part>,
        sql: String,
        leadingLineOffset: Int,
    ) {
        when (val outcome = parse(sql)) {
            is ParseOutcome.Failed -> reporter.reportOn(
                anchorSource(parts, outcome.reason, leadingLineOffset) ?: expression.source,
                KueryClientDiagnostics.KUERY_SQL_SYNTAX,
                outcome.reason,
            )
            // Only a statement that parses is feature-checked; a broken one already drew the
            // syntax warning.
            is ParseOutcome.Parsed -> {
                val reason = dialect?.let { dialectFailureOrNull(outcome.statements, it) } ?: return
                reporter.reportOn(
                    expression.source,
                    KueryClientDiagnostics.KUERY_SQL_DIALECT,
                    "$reason (dialect: $dialectLabel)",
                )
            }
            ParseOutcome.Indeterminate -> Unit
        }
    }

    // The texts of the block's statements if every one of them is an add/unaryPlus call on the
    // block's own builder with a statically reconstructable argument, or an inlinable helper
    // call, otherwise null.
    private fun reconstructedPartsOrNull(block: FirAnonymousFunction): List<Part>? {
        val statements = block.body?.statements ?: return null
        val numbering = SqlTextReconstruction.ParameterNumbering()
        val owners = thisOwnersOf(block.symbol, block.receiverParameter?.symbol)
        return statements.map { partOrNull(it, owners, numbering) ?: return null }
    }

    private fun partOrNull(
        statement: FirStatement,
        owners: Set<FirBasedSymbol<*>>,
        numbering: SqlTextReconstruction.ParameterNumbering,
    ): Part? {
        val call = statement.unwrapImplicitReturn() as? FirFunctionCall ?: return null
        val text = callTextOrNull(call, owners, numbering, seen = mutableSetOf(), depth = 0) ?: return null
        return Part(text, SqlBuilderCalls.sqlArgumentOrNull(call)?.source ?: call.source)
    }

    // The SQL text a single call contributes: an add/unaryPlus on the current builder, or an
    // inlined helper call.
    private fun callTextOrNull(
        call: FirFunctionCall,
        owners: Set<FirBasedSymbol<*>>,
        numbering: SqlTextReconstruction.ParameterNumbering,
        seen: MutableSet<FirBasedSymbol<*>>,
        depth: Int,
    ): String? {
        val sqlArgument = SqlBuilderCalls.sqlArgumentOrNull(call)
        return if (sqlArgument != null) {
            if (call.dispatchReceiver.isThisBoundTo(owners)) addTextOrNull(sqlArgument, owners, numbering) else null
        } else {
            inlinedHelperTextOrNull(call, owners, numbering, seen, depth)
        }
    }

    private fun addTextOrNull(
        sqlArgument: FirExpression,
        owners: Set<FirBasedSymbol<*>>,
        numbering: SqlTextReconstruction.ParameterNumbering,
    ): String? {
        val text = SqlTextReconstruction.textOrNull(sqlArgument, numbering, owners) ?: return null
        // The runtime auto-trim applies per add/unaryPlus call, so trim each text here rather
        // than per block statement (an inlined helper contributes several adds in one statement).
        return if (autoTrimIndent) text.trimIndent() else text
    }

    // A same-module, final SqlBuilder-extension helper whose body is only static adds on its own
    // receiver (or further such helpers) is inlined into the reconstruction: its interpolations
    // become the same :pN binds at runtime regardless of the call arguments, so the numbering
    // simply continues through the body. A compiled helper from another module has no body here
    // and returns null (skip), as does anything dynamic — including argument or default-value
    // expressions that could themselves reach a builder and append fragments of their own.
    private fun inlinedHelperTextOrNull(
        call: FirFunctionCall,
        callerOwners: Set<FirBasedSymbol<*>>,
        numbering: SqlTextReconstruction.ParameterNumbering,
        seen: MutableSet<FirBasedSymbol<*>>,
        depth: Int,
    ): String? {
        val helper = helperFunctionOrNull(call, callerOwners) ?: return null
        // `seen` tracks the current recursion stack, so sibling calls to the same helper are
        // fine while direct or mutual recursion bails out.
        if (depth >= MAX_HELPER_DEPTH || !seen.add(helper.symbol)) return null
        val owners = thisOwnersOf(helper.symbol, helper.receiverParameter?.symbol)
        val texts = helper.body?.statements.orEmpty().map { statement ->
            (statement.unwrapImplicitReturn() as? FirFunctionCall)
                ?.let { callTextOrNull(it, owners, numbering, seen, depth + 1) }
                ?: return null
        }
        seen.remove(helper.symbol)
        return texts.joinToString("\n")
    }

    // The add call whose reconstructed lines contain the failing line; the parser may also point
    // one past the end (EOF), which maps to the last add. Parser positions are relative to the
    // trimmed SQL, so shift them back by the fully removed leading lines.
    private fun anchorSource(
        parts: List<Part>,
        reason: String,
        leadingLineOffset: Int,
    ): KtSourceElement? {
        val reported = LINE_REGEX.find(reason)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val line = reported + leadingLineOffset
        var firstLine = 1
        for (part in parts) {
            // A part ending in '\r' merges with the joining '\n' into a single CRLF break, so
            // the part spans one line fewer than lines() reports.
            val lineCount = part.text.lines().size - (if (part.text.endsWith('\r')) 1 else 0)
            if (line < firstLine + lineCount) return part.source
            firstLine += lineCount
        }
        return parts.last().source
    }

    private sealed interface ParseOutcome {
        class Parsed(val statements: List<Statement>) : ParseOutcome

        class Failed(val reason: String) : ParseOutcome

        // The parser itself failed (timeout, internal error): an internal problem must not
        // surface as a syntax warning nor break the user's build, so the check fails open.
        object Indeterminate : ParseOutcome
    }

    companion object {
        // KueryClient.sql([sqlId,] block) / KueryBlockingClient.sql([sqlId,] block)
        private val SQL_MEMBER_NAME = Name.identifier("sql")

        // JSqlParser reports positions within the reconstructed SQL as "at line N, column M".
        private val LINE_REGEX = Regex("""at line (\d+), column \d+""")
        private val WHITESPACE_REGEX = Regex("""\s+""")

        // One shared daemon thread for every parse: JSqlParser's own single-argument overload
        // creates a fresh non-daemon executor per call and leaks it on the failure path.
        private val parserExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "kuery-client-sql-syntax-check").apply { isDaemon = true }
        }

        private const val MAX_HELPER_DEPTH = 10

        private fun thisOwnersOf(vararg symbols: FirBasedSymbol<*>?): Set<FirBasedSymbol<*>> =
            symbols.filterNotNull().toSet()

        // add/unaryPlus must act on the current builder — the `this` of the enclosing block
        // lambda or inlined helper; a call on any other SqlBuilder contributes to a different
        // statement at runtime.
        private fun FirExpression?.isThisBoundTo(owners: Set<FirBasedSymbol<*>>): Boolean {
            val reference = (this as? FirThisReceiverExpression)?.calleeReference ?: return false
            val bound = reference.boundSymbol as? FirBasedSymbol<*> ?: return false
            return bound in owners
        }

        // An open helper can be overridden at runtime, so only a final one has a single known
        // body.
        @OptIn(SymbolInternals::class)
        private fun helperFunctionOrNull(
            call: FirFunctionCall,
            callerOwners: Set<FirBasedSymbol<*>>,
        ): FirNamedFunction? {
            if (!call.isInlinableHelperCall(callerOwners)) return null
            val symbol = call.calleeReference.toResolvedCallableSymbol() as? FirNamedFunctionSymbol ?: return null
            if (symbol.resolvedStatus.modality != Modality.FINAL) return null
            return symbol.fir.takeIf { it.hasInlinableBody() }
        }

        // The helper must act on the caller's builder (its extension receiver), a this-bound
        // dispatch receiver (a member helper of the enclosing class) being harmless — and its
        // argument expressions must not be able to reach a builder, because they run against it
        // before the helper body does.
        private fun FirFunctionCall.isInlinableHelperCall(callerOwners: Set<FirBasedSymbol<*>>): Boolean =
            extensionReceiver.isThisBoundTo(callerOwners) &&
                (dispatchReceiver == null || dispatchReceiver is FirThisReceiverExpression) &&
                argumentList.arguments.none { it.containsThisBoundTo(callerOwners) }

        // A helper compiled in another module is deserialized without a body (and a cross-file
        // body may still be lazy under the IDE's LL FIR); a default-value expression that can
        // reach the helper's own receiver would append fragments before the body runs.
        private fun FirNamedFunction.hasInlinableBody(): Boolean {
            val body = this.body
            val owners = thisOwnersOf(symbol, receiverParameter?.symbol)
            return body != null &&
                body !is FirLazyBlock &&
                valueParameters.none { it.defaultValue?.containsThisBoundTo(owners) == true }
        }

        // A lambda body's last expression may be wrapped in an implicit return.
        private fun FirStatement.unwrapImplicitReturn(): FirStatement = (this as? FirReturnExpression)?.result ?: this

        // InstanceOfCheckForException: the parser failure is wrapped in a cause chain, so the
        // interesting exception type genuinely has to be located by walking it.
        // Vendor statements JSqlParser has no grammar for at all; reporting them would be a
        // permanent false positive, so a failed parse that matches one is skipped instead.
        // Currently: H2's `MERGE INTO ... KEY(...)` upsert.
        private val KNOWN_UNPARSEABLE_STATEMENTS = listOf(
            Regex("""(?is)\bMERGE\s+INTO\b.*\bKEY\s*\("""),
        )

        @Suppress("TooGenericExceptionCaught", "SwallowedException", "InstanceOfCheckForException")
        private fun parse(sql: String): ParseOutcome = try {
            ParseOutcome.Parsed(CCJSqlParserUtil.parseStatements(sql, parserExecutor) {})
        } catch (e: JSQLParserException) {
            // Walk to the actual parser failure: the util wraps it as
            // JSQLParserException(ExecutionException(ParseException)). A chain without one is
            // not a syntax problem (e.g. the parse timeout) and must not be reported as one.
            val reason = generateSequence(e as Throwable) { it.cause }
                .firstOrNull { it is ParseException || it is TokenMgrException }
                ?.message
            when {
                reason == null -> ParseOutcome.Indeterminate
                KNOWN_UNPARSEABLE_STATEMENTS.any { it.containsMatchIn(sql) } -> ParseOutcome.Indeterminate
                else -> ParseOutcome.Failed(firstSentenceOf(reason))
            }
        } catch (e: Exception) {
            ParseOutcome.Indeterminate
        }

        // The dialect feature violations (e.g. "insertUseDuplicateKeyUpdate not supported."),
        // or null when the statements fit the dialect. Fails open like parse.
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        private fun dialectFailureOrNull(
            statements: List<Statement>,
            dialect: DatabaseType,
        ): String? = try {
            val context = Validation.createValidationContext(FeatureConfiguration(), listOf(dialect))
            val messages = statements
                .flatMap { statement -> Validation.validate(statement, context).values.flatten() }
                .mapNotNull { it.message }
            if (messages.isEmpty()) null else messages.joinToString(" ")
        } catch (e: Exception) {
            null
        }

        // JSqlParser messages start with "Encountered ... at line N, column M." and then list
        // every expected token over many lines; keep only the useful first part.
        private fun firstSentenceOf(message: String): String = message
            .lineSequence()
            .takeWhile { !it.startsWith("Was expecting") }
            .joinToString(" ")
            .trim()
            .replace(WHITESPACE_REGEX, " ")
    }
}

// The only place the vendor enum appears: our public check vocabulary mapped onto the parser
// currently backing the check. GENERIC parses without any feature validation.
private fun SqlSyntaxCheck.toDatabaseTypeOrNull(): DatabaseType? = when (this) {
    SqlSyntaxCheck.GENERIC -> null
    SqlSyntaxCheck.ANSI -> DatabaseType.ANSI_SQL
    SqlSyntaxCheck.ORACLE -> DatabaseType.ORACLE
    SqlSyntaxCheck.MYSQL -> DatabaseType.MYSQL
    SqlSyntaxCheck.SQLSERVER -> DatabaseType.SQLSERVER
    SqlSyntaxCheck.MARIADB -> DatabaseType.MARIADB
    SqlSyntaxCheck.POSTGRESQL -> DatabaseType.POSTGRESQL
    SqlSyntaxCheck.H2 -> DatabaseType.H2
}
