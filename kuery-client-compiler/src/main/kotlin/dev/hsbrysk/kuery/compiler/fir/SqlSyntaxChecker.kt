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
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.expressions.FirAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirReturnExpression
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.FirThisReceiverExpression
import org.jetbrains.kotlin.fir.expressions.unwrapArgument
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.lookupSuperTypes
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Validates the SQL syntax of a `sql { ... }` / `Sql { ... }` block at compile time, only when
 * the complete statement is statically known: every statement of the block must be an
 * add/unaryPlus call on the block's own builder whose text [SqlTextReconstruction] can
 * reconstruct. Blocks containing anything else (conditionals, helper calls, addUnsafe,
 * variables, calls on another builder, ...) are silently skipped — a dynamically assembled
 * statement cannot be reconstructed, and guessing would produce false positives.
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

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        val block = sqlBlockOrNull(expression, context.session) ?: return
        val parts = reconstructedPartsOrNull(block) ?: return
        val sql = parts.joinToString("\n") { it.text }
        if (sql.isBlank()) return
        reportFailures(expression, parts, sql)
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun reportFailures(
        expression: FirFunctionCall,
        parts: List<Part>,
        sql: String,
    ) {
        when (val outcome = parse(sql)) {
            is ParseOutcome.Failed -> reporter.reportOn(
                anchorSource(parts, outcome.reason) ?: expression.source,
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
    // block's own builder with a statically reconstructable argument, otherwise null.
    private fun reconstructedPartsOrNull(block: FirAnonymousFunction): List<Part>? {
        val statements = block.body?.statements ?: return null
        val numbering = SqlTextReconstruction.ParameterNumbering()
        return statements.map { partOrNull(it, block, numbering) ?: return null }
    }

    private fun partOrNull(
        statement: FirStatement,
        block: FirAnonymousFunction,
        numbering: SqlTextReconstruction.ParameterNumbering,
    ): Part? {
        val call = statement.unwrapImplicitReturn() as? FirFunctionCall ?: return null
        val sqlArgument = SqlBuilderCalls.sqlArgumentOrNull(call)?.takeIf { call.isOnOwnBuilder(block) } ?: return null
        val text = SqlTextReconstruction.textOrNull(sqlArgument, numbering) ?: return null
        return Part(
            if (autoTrimIndent) text.trimIndent() else text,
            sqlArgument.source ?: call.source,
        )
    }

    // The add call whose reconstructed lines contain the failing line; the parser may also point
    // one past the end (EOF), which maps to the last add.
    private fun anchorSource(
        parts: List<Part>,
        reason: String,
    ): KtSourceElement? {
        val line = LINE_REGEX.find(reason)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        var firstLine = 1
        for (part in parts) {
            val lineCount = part.text.lines().size
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

        private fun sqlBlockOrNull(
            call: FirFunctionCall,
            session: FirSession,
        ): FirAnonymousFunction? {
            // This checker runs on every function call in the project, so pre-filter on the
            // callee name before any symbol resolution.
            val isEntryPoint = when (call.calleeReference.name) {
                CallableIds.SQL_TOP_LEVEL.callableName ->
                    call.calleeReference.toResolvedCallableSymbol()?.callableId == CallableIds.SQL_TOP_LEVEL
                SQL_MEMBER_NAME -> call.isClientSqlCall(session)
                else -> false
            }
            if (!isEntryPoint) return null
            return call.argumentList.arguments
                .asSequence()
                .map { it.unwrapArgument() }
                .filterIsInstance<FirAnonymousFunctionExpression>()
                .singleOrNull()
                ?.anonymousFunction
        }

        // Matching by the receiver's type rather than the resolved callable covers both
        // sub-interfaces (fake overrides) and overriding decorators such as
        // `class Decorated(d: KueryClient) : KueryClient by d`, mirroring the IR-side override
        // walk in SqlIdInjectionTransformer.
        private fun FirFunctionCall.isClientSqlCall(session: FirSession): Boolean {
            val receiverType = dispatchReceiver?.resolvedType?.fullyExpandedType(session) ?: return false
            val classSymbol = receiverType.toRegularClassSymbol(session) ?: return false
            if (classSymbol.classId in ClassIds.SQL_CLIENTS) return true
            return lookupSuperTypes(classSymbol, lookupInterfaces = true, deep = true, useSiteSession = session)
                .any { it.classId in ClassIds.SQL_CLIENTS }
        }

        // add/unaryPlus must act on this block's own builder (the lambda's receiver); a call on
        // any other SqlBuilder contributes to a different statement at runtime.
        private fun FirFunctionCall.isOnOwnBuilder(block: FirAnonymousFunction): Boolean {
            val thisReference = (dispatchReceiver as? FirThisReceiverExpression)?.calleeReference ?: return false
            val bound = thisReference.boundSymbol
            return bound == block.symbol || bound == block.receiverParameter?.symbol
        }

        // A lambda body's last expression may be wrapped in an implicit return.
        private fun FirStatement.unwrapImplicitReturn(): FirStatement = (this as? FirReturnExpression)?.result ?: this

        // InstanceOfCheckForException: the parser failure is wrapped in a cause chain, so the
        // interesting exception type genuinely has to be located by walking it.
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
            if (reason != null) ParseOutcome.Failed(firstSentenceOf(reason)) else ParseOutcome.Indeterminate
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

        // The only place the vendor enum appears: our public check vocabulary mapped onto the
        // parser currently backing the check. GENERIC parses without any feature validation.
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
