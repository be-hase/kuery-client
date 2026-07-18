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
 * enabled (this checker is only registered then): the plugin applies trimIndent anyway, so the
 * explicit call only adds runtime work — and on a literal/template receiver it also prevents the
 * compile-time folding.
 *
 * Flagged is any trimIndent that is the outermost call of the argument (or of an if/when branch
 * result), regardless of the receiver. A non-compile-time-safe receiver (e.g. a variable) also
 * draws [KueryClientDiagnostics.KUERY_UNSAFE_SQL_STRING]; the two warnings state independent
 * facts (the string cannot be verified / the trim is redundant), so both are reported.
 * `trimMargin` is never flagged — auto-trim does not remove margins, so an explicit call is not
 * redundant.
 */
internal class RedundantTrimIndentChecker(private val diagnostics: KueryClientDiagnostics) :
    FirFunctionCallChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        val sqlArgument = SqlBuilderCalls.sqlArgumentOrNull(expression) ?: return
        reportRedundantTrims(sqlArgument)
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun reportRedundantTrims(expression: FirExpression) {
        when (expression) {
            is FirFunctionCall -> {
                if (expression.calleeReference.toResolvedCallableSymbol()?.callableId == CallableIds.TRIM_INDENT) {
                    reporter.reportOn(expression.source, diagnostics.KUERY_REDUNDANT_TRIM_INDENT)
                }
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
