package dev.hsbrysk.kuery.compiler.fir

import dev.hsbrysk.kuery.compiler.misc.CallableIds
import org.jetbrains.kotlin.fir.declarations.utils.isConst
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirStringConcatenationCall
import org.jetbrains.kotlin.fir.expressions.unwrapArgument
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.types.ConstantValueKind

/**
 * Reconstructs, at compile time, the SQL text an add/unaryPlus argument produces at runtime:
 * the FIR counterpart of [dev.hsbrysk.kuery.compiler.ir.misc.StringConcatenationProcessor], with
 * every interpolated value replaced by the `:pN` placeholder that
 * `DefaultSqlBuilder.interpolate` assigns to it (a `:pN` never contains whitespace, so
 * trimIndent/trimMargin applied to the reconstructed text behaves exactly like the runtime
 * folding in [dev.hsbrysk.kuery.compiler.ir.misc.TrimFolding]).
 *
 * Returns null for any shape whose text is not fully determined at compile time (variables,
 * if/when results, non-literal const initializers, ...); callers treat null as "skip the check".
 */
internal object SqlTextReconstruction {
    /** Mirrors the per-builder `p0, p1, ...` numbering of `DefaultSqlBuilder.bind`. */
    class ParameterNumbering {
        private var next = 0

        fun nextPlaceholder(): String = ":p${next++}"
    }

    fun textOrNull(
        expression: FirExpression,
        numbering: ParameterNumbering,
    ): String? = when (expression) {
        is FirLiteralExpression -> expression.textOrNull()
        is FirStringConcatenationCall ->
            expression.argumentList.arguments.joinToString("") { argument ->
                fragmentTextOrNull(argument.unwrapArgument()) ?: numbering.nextPlaceholder()
            }
        is FirPropertyAccessExpression -> expression.constValueTextOrNull()
        is FirFunctionCall -> expression.trimmedTextOrNull(numbering)
        else -> null
    }

    // A string-template argument that contributes SQL text rather than a bind parameter. Keep in
    // sync with StringConcatenationProcessor.fragmentTextOrNull (String/Char constants) plus the
    // const-val inlining performed by FIR2IR.
    private fun fragmentTextOrNull(expression: FirExpression): String? = when (expression) {
        is FirLiteralExpression -> expression.textOrNull()
        is FirPropertyAccessExpression -> expression.constValueTextOrNull()
        else -> null
    }

    private fun FirLiteralExpression.textOrNull(): String? = when (kind) {
        ConstantValueKind.String, ConstantValueKind.Char -> value.toString()
        else -> null
    }

    private fun FirPropertyAccessExpression.constValueTextOrNull(): String? {
        val symbol = calleeReference.toResolvedCallableSymbol() as? FirPropertySymbol ?: return null
        if (!symbol.isConst) return null
        return (symbol.resolvedInitializer as? FirLiteralExpression)?.textOrNull()
    }

    // "...".trimIndent() / "...".trimMargin(<literal>): reconstruct the receiver, then apply the
    // trim to the placeholder-substituted text (equivalent to the runtime order, where the trim
    // runs on the interpolated string whose values are already :pN placeholders).
    private fun FirFunctionCall.trimmedTextOrNull(numbering: ParameterNumbering): String? {
        val receiver = extensionReceiver ?: return null
        val receiverText = { textOrNull(receiver, numbering) }
        return when (calleeReference.toResolvedCallableSymbol()?.callableId) {
            CallableIds.TRIM_INDENT -> receiverText()?.trimIndent()
            CallableIds.TRIM_MARGIN -> {
                val prefix = when (val argument = argumentList.arguments.firstOrNull()?.unwrapArgument()) {
                    null -> "|"
                    else -> (argument as? FirLiteralExpression)?.value as? String ?: return null
                }
                receiverText()?.trimMargin(prefix)
            }
            else -> null
        }
    }
}
