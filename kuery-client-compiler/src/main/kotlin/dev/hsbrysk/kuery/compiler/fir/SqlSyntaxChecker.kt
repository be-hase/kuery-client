package dev.hsbrysk.kuery.compiler.fir

import net.sf.jsqlparser.JSQLParserException
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.util.validation.Validation
import net.sf.jsqlparser.util.validation.feature.DatabaseType
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.expressions.FirAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirReturnExpression
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.unwrapArgument
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Validates the SQL syntax of a `sql { ... }` / `Sql { ... }` block at compile time, only when
 * the complete statement is statically known: every statement of the block must be an
 * add/unaryPlus call whose text [SqlTextReconstruction] can reconstruct. Blocks containing
 * anything else (conditionals, helper calls, addUnsafe, variables, ...) are silently skipped —
 * a dynamically assembled statement cannot be reconstructed, and guessing would produce false
 * positives.
 *
 * The reconstructed texts are joined with newlines exactly like `DefaultSqlBuilder.addUnsafe`
 * accumulates them, then parsed with JSqlParser. A parse failure is reported as the
 * [KueryClientDiagnostics.KUERY_SQL_SYNTAX] warning, anchored to the add call the failing line
 * belongs to. JSqlParser is deliberately lenient, so this check trades detection power for a
 * low false-positive rate; any parser crash other than a parse failure fails open.
 *
 * When a [dialect] is configured, a statement that parses is additionally checked against that
 * dialect's feature allow-list (JSqlParser's validation framework) and violations are reported
 * as [KueryClientDiagnostics.KUERY_SQL_DIALECT] — e.g. `ON DUPLICATE KEY UPDATE` under
 * `postgresql`. This is feature-level, not a full dialect grammar: some cross-dialect syntax
 * still passes, which keeps the failure mode on the false-negative side.
 */
internal class SqlSyntaxChecker(
    private val autoTrimIndent: Boolean,
    private val dialect: DatabaseType?,
) : FirFunctionCallChecker(MppCheckerKind.Common) {
    private class Part(
        val text: String,
        val source: KtSourceElement?,
    )

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        val block = sqlBlockOrNull(expression) ?: return
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
        val reason = parseFailureOrNull(sql)
        if (reason != null) {
            reporter.reportOn(
                anchorSource(parts, reason) ?: expression.source,
                KueryClientDiagnostics.KUERY_SQL_SYNTAX,
                reason,
            )
            return
        }

        // Only a statement that parses is feature-checked; a broken one already drew the
        // syntax warning above.
        val dialectReason = dialect?.let { dialectFailureOrNull(sql, it) } ?: return
        reporter.reportOn(expression.source, KueryClientDiagnostics.KUERY_SQL_DIALECT, dialectReason)
    }

    // The texts of the block's statements if every one of them is an add/unaryPlus call with a
    // statically reconstructable argument, otherwise null.
    private fun reconstructedPartsOrNull(block: FirAnonymousFunction): List<Part>? {
        val statements = block.body?.statements ?: return null
        val numbering = SqlTextReconstruction.ParameterNumbering()
        return statements.map { partOrNull(it, numbering) ?: return null }
    }

    private fun partOrNull(
        statement: FirStatement,
        numbering: SqlTextReconstruction.ParameterNumbering,
    ): Part? {
        val call = statement.unwrapImplicitReturn() as? FirFunctionCall ?: return null
        val sqlArgument = SqlBuilderCalls.sqlArgumentOrNull(call) ?: return null
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

    companion object {
        private val CORE_PACKAGE = FqName("dev.hsbrysk.kuery.core")
        private val SQL_BLOCK_ENTRY_POINTS = setOf(
            // Sql(block)
            CallableId(CORE_PACKAGE, Name.identifier("Sql")),
            // KueryClient.sql([sqlId,] block) / KueryBlockingClient.sql([sqlId,] block)
            CallableId(ClassId(CORE_PACKAGE, Name.identifier("KueryClient")), Name.identifier("sql")),
            CallableId(ClassId(CORE_PACKAGE, Name.identifier("KueryBlockingClient")), Name.identifier("sql")),
        )

        // JSqlParser reports positions within the reconstructed SQL as "at line N, column M".
        private val LINE_REGEX = Regex("""at line (\d+), column \d+""")

        private fun sqlBlockOrNull(call: FirFunctionCall): FirAnonymousFunction? {
            val symbol = call.calleeReference.toResolvedCallableSymbol() ?: return null
            if (symbol.unwrapFakeOverrides().callableId !in SQL_BLOCK_ENTRY_POINTS) return null
            val lambda = call.argumentList.arguments.lastOrNull()?.unwrapArgument()
                as? FirAnonymousFunctionExpression ?: return null
            return lambda.anonymousFunction
        }

        // A lambda body's last expression may be wrapped in an implicit return.
        private fun FirStatement.unwrapImplicitReturn(): FirStatement = (this as? FirReturnExpression)?.result ?: this

        // The parse-failure reason, or null when the SQL parses (or the parser itself failed —
        // an internal parser error must not break the user's build, so the check fails open).
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        private fun parseFailureOrNull(sql: String): String? = try {
            CCJSqlParserUtil.parseStatements(sql)
            null
        } catch (e: JSQLParserException) {
            (e.cause ?: e).message?.let(::firstSentenceOf)
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
            .replace(Regex("""\s+"""), " ")

        // The dialect feature violations (e.g. "insertUseDuplicateKeyUpdate not supported."),
        // or null when the statement fits the dialect. Fails open like parseFailureOrNull.
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        private fun dialectFailureOrNull(
            sql: String,
            dialect: DatabaseType,
        ): String? = try {
            val messages = Validation(listOf(dialect), sql)
                .validate()
                .flatMap { it.errors }
                .mapNotNull { it.message }
            if (messages.isEmpty()) {
                null
            } else {
                "${messages.joinToString(" ")} (dialect: ${dialect.name.lowercase()})"
            }
        } catch (e: Exception) {
            null
        }
    }
}
