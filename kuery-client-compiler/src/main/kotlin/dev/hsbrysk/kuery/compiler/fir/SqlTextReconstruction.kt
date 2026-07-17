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
 * The reconstructable shapes are therefore a strict subset of
 * [CompileTimeSafeSqlStrings.isCompileTimeSafe] — when extending one, revisit the other.
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
        is FirStringConcatenationCall -> expression.templateTextOrNull(numbering)
        is FirPropertyAccessExpression -> expression.constPropertySymbolOrNull()?.literalInitializerTextOrNull()
        is FirFunctionCall -> expression.trimmedTextOrNull(numbering)
        else -> null
    }

    // Each template argument is one of three things — SQL text, a bind value (:pN), or unknown.
    // Keep in sync with StringConcatenationProcessor.fragmentTextOrNull (String/Char constants
    // become text) plus the const-val inlining performed by FIR2IR: a const reference is ALWAYS
    // inlined as text at runtime, so when its value cannot be computed here (a constant
    // expression rather than a single literal) the whole reconstruction must bail — substituting
    // a placeholder would diverge from the runtime SQL and produce false positives.
    // UnreachableCode: detekt's type resolution (Kotlin 1.9-based) cannot resolve the 2.4
    // compiler API and falsely flags the elvis-return as unreachable.
    @Suppress("UnreachableCode")
    private fun FirStringConcatenationCall.templateTextOrNull(numbering: ParameterNumbering): String? {
        val text = StringBuilder()
        for (argument in argumentList.arguments) {
            val unwrapped = argument.unwrapArgument()
            val constSymbol = unwrapped.constPropertySymbolOrNull()
            when {
                unwrapped is FirLiteralExpression ->
                    text.append(unwrapped.textOrNull() ?: numbering.nextPlaceholder())
                constSymbol != null ->
                    text.append(constSymbol.literalInitializerTextOrNull() ?: return null)
                else -> text.append(numbering.nextPlaceholder())
            }
        }
        return text.toString()
    }

    private fun FirLiteralExpression.textOrNull(): String? = when (kind) {
        ConstantValueKind.String, ConstantValueKind.Char -> value.toString()
        else -> null
    }

    private fun FirExpression.constPropertySymbolOrNull(): FirPropertySymbol? =
        ((this as? FirPropertyAccessExpression)?.calleeReference?.toResolvedCallableSymbol() as? FirPropertySymbol)
            ?.takeIf { it.isConst }

    private fun FirPropertySymbol.literalInitializerTextOrNull(): String? =
        (resolvedInitializer as? FirLiteralExpression)?.textOrNull()

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
                // trimMargin(blank) throws at runtime, so the block never yields SQL — and the
                // same exception must certainly not be thrown here inside the checker.
                if (prefix.isBlank()) return null
                receiverText()?.trimMargin(prefix)
            }
            else -> null
        }
    }
}
