package dev.hsbrysk.kuery.compiler.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid

/**
 * Reports `KUERY_BIND_CALL_IN_SQL_TEMPLATE` when `SqlBuilder.bind()` is called
 * anywhere inside the SQL string argument of `add()` / `unaryPlus`. The IR transformation converts every
 * interpolated value into a bind parameter, so the placeholder name returned by `bind()` would itself be
 * re-bound as a new parameter value and the SQL would compare against the literal string ":pN".
 * `bind()` is only meaningful together with `addUnsafe()`, which is not transformed.
 */
internal class BindCallInSqlTemplateChecker(private val diagnostics: KueryClientDiagnostics) :
    FirFunctionCallChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        val sqlArgument = SqlBuilderCalls.sqlArgumentOrNull(expression) ?: return
        collectBindCalls(sqlArgument).forEach {
            reporter.reportOn(it.source ?: expression.source, diagnostics.KUERY_BIND_CALL_IN_SQL_TEMPLATE)
        }
    }

    private fun collectBindCalls(sqlArgument: FirExpression): List<FirFunctionCall> {
        val found = mutableListOf<FirFunctionCall>()
        sqlArgument.accept(
            object : FirVisitorVoid() {
                override fun visitElement(element: FirElement) {
                    if (
                        element is FirFunctionCall &&
                        element.calleeReference.toResolvedCallableSymbol()?.callableId == SqlBuilderCalls.BIND
                    ) {
                        found.add(element)
                    }
                    element.acceptChildren(this)
                }
            },
        )
        return found
    }
}
