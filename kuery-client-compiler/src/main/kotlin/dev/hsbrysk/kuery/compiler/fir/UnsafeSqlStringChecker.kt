package dev.hsbrysk.kuery.compiler.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall

internal object UnsafeSqlStringChecker : FirFunctionCallChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        val sqlArgument = SqlBuilderCalls.sqlArgumentOrNull(expression) ?: return
        if (CompileTimeSafeSqlStrings.isCompileTimeSafe(sqlArgument)) {
            return
        }
        reporter.reportOn(sqlArgument.source ?: expression.source, KueryClientDiagnostics.KUERY_UNSAFE_SQL_STRING)
    }
}
