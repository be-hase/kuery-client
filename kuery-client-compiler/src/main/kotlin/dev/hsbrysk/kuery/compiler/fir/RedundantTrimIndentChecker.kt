package dev.hsbrysk.kuery.compiler.fir

import dev.hsbrysk.kuery.compiler.misc.CallableIds
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirWhenExpression
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol

/**
 * Reports an explicit `trimIndent()` on the SQL string of add/unaryPlus when `autoTrimIndent` is
 * enabled (this checker is only registered then). Such a call is not just redundant — it is the
 * pessimal path: the argument is no longer a plain template, so the plugin cannot fold the trim
 * at compile time and instead the string is trimmed twice at runtime.
 *
 * Flagged shapes mirror [CompileTimeSafeSqlStrings.isCompileTimeSafe]: the trimIndent must be
 * the outermost call of the argument (or of an if/when branch result) over a compile-time-safe
 * receiver — including trim chains like `"...".trimMargin().trimIndent()`. Receivers the shared
 * predicate rejects (e.g. a variable) are left to
 * [KueryClientDiagnostics.KUERY_UNSAFE_SQL_STRING] so the same expression never gets both
 * warnings. `trimMargin` is never flagged — auto-trim does not remove margins, so an explicit
 * call is not redundant.
 */
internal object RedundantTrimIndentChecker : FirFunctionCallChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        val sqlArgument = SqlBuilderCalls.sqlArgumentOrNull(expression) ?: return
        reportRedundantTrims(sqlArgument)
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun reportRedundantTrims(expression: FirExpression) {
        when (expression) {
            is FirFunctionCall -> {
                if (expression.calleeReference.toResolvedCallableSymbol()?.callableId != CallableIds.TRIM_INDENT) {
                    return
                }
                val receiver = expression.extensionReceiver ?: return
                if (!CompileTimeSafeSqlStrings.isCompileTimeSafe(receiver)) {
                    return
                }
                reporter.reportOn(expression.source, KueryClientDiagnostics.KUERY_REDUNDANT_TRIM_INDENT)
            }
            // The automatic trim applies to whichever branch value is selected, so a trimIndent
            // that is the outermost call of a branch result is just as redundant.
            is FirWhenExpression ->
                expression.branches.forEach { branch ->
                    branch.resultExpressionOrNull()?.let { reportRedundantTrims(it) }
                }
            else -> Unit
        }
    }
}
