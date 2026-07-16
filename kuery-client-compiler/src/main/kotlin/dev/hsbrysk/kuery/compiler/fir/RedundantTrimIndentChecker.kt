package dev.hsbrysk.kuery.compiler.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.declarations.utils.isConst
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirStringConcatenationCall
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Reports an explicit `trimIndent()` on the SQL string of add/unaryPlus when `autoTrimIndent` is
 * enabled (this checker is only registered then). Such a call is not just redundant — it is the
 * pessimal path: the argument is no longer a plain template, so the plugin cannot fold the trim
 * at compile time and instead the string is trimmed twice at runtime.
 *
 * Only the compile-time-safe receivers (literal/template/const val) are flagged; any other
 * receiver already gets [KueryClientDiagnostics.KUERY_UNSAFE_SQL_STRING], and a second warning
 * on the same expression would be noise. `trimMargin` is never flagged — auto-trim does not
 * remove margins, so an explicit call is not redundant.
 */
internal object RedundantTrimIndentChecker : FirFunctionCallChecker(MppCheckerKind.Common) {
    private val TRIM_INDENT = CallableId(FqName("kotlin.text"), Name.identifier("trimIndent"))

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        val trimCall = SqlBuilderCalls.sqlArgumentOrNull(expression) as? FirFunctionCall ?: return
        if (trimCall.calleeReference.toResolvedCallableSymbol()?.callableId != TRIM_INDENT) {
            return
        }
        val receiver = trimCall.extensionReceiver ?: return
        if (!receiver.isCompileTimeSafeReceiver()) {
            return
        }
        reporter.reportOn(
            trimCall.source ?: expression.source,
            KueryClientDiagnostics.KUERY_REDUNDANT_TRIM_INDENT,
        )
    }

    private fun FirExpression.isCompileTimeSafeReceiver(): Boolean = when (this) {
        is FirLiteralExpression -> true
        is FirStringConcatenationCall -> true
        is FirPropertyAccessExpression -> calleeReference.toResolvedCallableSymbol()?.isConst == true
        else -> false
    }
}
