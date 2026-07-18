package dev.hsbrysk.kuery.compiler.fir

import dev.hsbrysk.kuery.compiler.misc.CallableIds
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.declarations.utils.isConst
import org.jetbrains.kotlin.fir.declarations.utils.isStatic
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirStringConcatenationCall
import org.jetbrains.kotlin.fir.expressions.FirThisReceiverExpression
import org.jetbrains.kotlin.fir.expressions.unwrapArgument
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirFieldSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeFlexibleType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.name.StandardClassIds
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
 * if/when results, unresolvable constant values, values that can reach the enclosing builder,
 * ...); callers treat null as "skip the check". The reconstructable shapes are therefore a
 * strict subset of [CompileTimeSafeSqlStrings.isCompileTimeSafe] — when extending one, revisit
 * the other.
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
        builderOwners: Set<FirBasedSymbol<*>>,
    ): String? = when (expression) {
        is FirLiteralExpression -> expression.textOrNull()
        is FirStringConcatenationCall -> expression.templateTextOrNull(numbering, builderOwners)
        // A direct (non-template) argument of add() must be SQL text; anything non-constant is
        // not statically known.
        is FirPropertyAccessExpression -> (classifyValue(expression, builderOwners) as? ValueKind.Text)?.text
        is FirFunctionCall -> expression.trimmedTextOrNull(numbering, builderOwners)
        else -> null
    }

    // Each template argument is one of three things — SQL text, a bind value (:pN), or a reason
    // to give up. Keep in sync with StringConcatenationProcessor.fragmentTextOrNull (String/Char
    // constants become text) plus the constant inlining performed by FIR2IR.
    // UnreachableCode: detekt's type resolution (Kotlin 1.9-based) cannot resolve the 2.4
    // compiler API and falsely flags the early returns as unreachable.
    @Suppress("UnreachableCode")
    private fun FirStringConcatenationCall.templateTextOrNull(
        numbering: ParameterNumbering,
        builderOwners: Set<FirBasedSymbol<*>>,
    ): String? {
        val text = StringBuilder()
        for (argument in argumentList.arguments) {
            val unwrapped = argument.unwrapArgument()
            if (unwrapped is FirLiteralExpression) {
                text.append(unwrapped.textOrNull() ?: numbering.nextPlaceholder())
                continue
            }
            when (val value = classifyValue(unwrapped, builderOwners)) {
                is ValueKind.Text -> text.append(value.text)
                ValueKind.Bind -> text.append(numbering.nextPlaceholder())
                ValueKind.Bail -> return null
            }
        }
        return text.toString()
    }

    private sealed interface ValueKind {
        class Text(val text: String) : ValueKind

        object Bind : ValueKind

        object Bail : ValueKind
    }

    // How a non-literal value behaves at runtime: FIR2IR folds compile-time String/Char
    // constants — Kotlin const vals AND Java constant fields — into the SQL TEXT, while every
    // other value is bound as :pN. A String/Char constant whose value cannot be computed here,
    // or an expression that can reach the enclosing builder (and thus append fragments of its
    // own before this add), makes the whole reconstruction give up.
    @OptIn(SymbolInternals::class)
    private fun classifyValue(
        expression: FirExpression,
        builderOwners: Set<FirBasedSymbol<*>>,
    ): ValueKind {
        val symbol = (expression as? FirPropertyAccessExpression)?.calleeReference?.toResolvedCallableSymbol()
        return when {
            symbol is FirPropertySymbol && symbol.isConst ->
                textConstantOrNull(symbol.isStringOrCharTyped(), symbol.resolvedInitializer)
            symbol is FirFieldSymbol && symbol.isStatic && symbol.fir.isVal ->
                textConstantOrNull(symbol.isStringOrCharTyped(), symbol.fir.initializer)
            expression.containsThisBoundTo(builderOwners) -> ValueKind.Bail
            else -> ValueKind.Bind
        }
    }

    private fun textConstantOrNull(
        isStringOrChar: Boolean,
        initializer: FirExpression?,
    ): ValueKind = when {
        // Non-String/Char constants are never spliced as text; they are bound like any value.
        !isStringOrChar -> ValueKind.Bind
        else -> (initializer as? FirLiteralExpression)?.textOrNull()?.let { ValueKind.Text(it) } ?: ValueKind.Bail
    }

    private fun FirBasedSymbol<*>.isStringOrCharTyped(): Boolean {
        val type = when (this) {
            is FirPropertySymbol -> resolvedReturnTypeRef.coneType
            is FirFieldSymbol -> resolvedReturnTypeRef.coneType
            else -> return false
        }
        return type.isStringOrChar()
    }

    // Session-free String/Char detection; Java field types are flexible (String!), so compare
    // on the lower bound.
    private fun ConeKotlinType.isStringOrChar(): Boolean {
        val rigid = (this as? ConeFlexibleType)?.lowerBound ?: this
        val classId = (rigid as? ConeClassLikeType)?.lookupTag?.classId
        return classId == StandardClassIds.String || classId == StandardClassIds.Char
    }

    private fun FirLiteralExpression.textOrNull(): String? = when (kind) {
        ConstantValueKind.String, ConstantValueKind.Char -> value.toString()
        else -> null
    }

    // "...".trimIndent() / "...".trimMargin(<literal>): reconstruct the receiver, then apply the
    // trim to the placeholder-substituted text (equivalent to the runtime order, where the trim
    // runs on the interpolated string whose values are already :pN placeholders).
    private fun FirFunctionCall.trimmedTextOrNull(
        numbering: ParameterNumbering,
        builderOwners: Set<FirBasedSymbol<*>>,
    ): String? {
        val receiver = extensionReceiver ?: return null
        val receiverText = { textOrNull(receiver, numbering, builderOwners) }
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

/**
 * Whether the expression tree contains a `this` reference bound to one of [owners] — i.e.
 * evaluating it could read or mutate the enclosing builder (e.g. a helper call or lambda that
 * appends its own fragments).
 */
internal fun FirElement.containsThisBoundTo(owners: Set<FirBasedSymbol<*>>): Boolean {
    var found = false
    val visitor = object : FirVisitorVoid() {
        override fun visitElement(element: FirElement) {
            if (found) return
            if (element is FirThisReceiverExpression &&
                (element.calleeReference.boundSymbol as? FirBasedSymbol<*>) in owners
            ) {
                found = true
                return
            }
            element.acceptChildren(this)
        }
    }
    accept(visitor)
    return found
}
