package dev.hsbrysk.kuery.compiler.ir

import dev.hsbrysk.kuery.compiler.ir.misc.CallableIds
import dev.hsbrysk.kuery.compiler.ir.misc.ClassIds
import dev.hsbrysk.kuery.compiler.ir.misc.ClassNames
import dev.hsbrysk.kuery.compiler.ir.misc.StringConcatenationProcessor
import dev.hsbrysk.kuery.compiler.ir.misc.TrimFolding
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStringConcatenation
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.irCastIfNeeded
import org.jetbrains.kotlin.ir.util.isVararg
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.parentClassOrNull

@Suppress("OPT_IN_USAGE")
class StringInterpolationTransformer(
    private val pluginContext: IrPluginContext,
    private val autoTrimIndent: Boolean = false,
) : IrElementTransformerVoidWithContext() {
    // The SqlBuilder receiver of the enclosing add/unaryPlus call, hoisted into a temporary
    // variable so that both the addUnsafe call and the interpolate call can reference it
    // without sharing (and thus double-evaluating) the original receiver expression.
    private var current: IrVariable? = null

    override fun visitCall(expression: IrCall): IrExpression {
        if (!expression.isAddOrUnaryPlus()) {
            return super.visitCall(expression)
        }

        // Save/restore so that a nested add/unaryPlus inside the argument expression
        // doesn't clear the enclosing call's context.
        val previous = current
        return try {
            val receiver = checkNotNull(expression.dispatchReceiver).transform(this, null)
            val temporary = checkNotNull(currentScope).scope.createTemporaryVariable(
                receiver,
                nameHint = "kueryClientSqlBuilder",
            )
            current = temporary

            // The SQL argument is the single non-dispatch parameter: `add`'s regular parameter
            // or `unaryPlus`'s extension receiver.
            val sqlArgumentParameter = expression.symbol.owner.parameters.first {
                it.kind != IrParameterKind.DispatchReceiver
            }
            val rawArgument = checkNotNull(expression.arguments[sqlArgumentParameter.indexInParameters])
            val sqlArgument = if (autoTrimIndent) {
                autoTrimmedArgument(rawArgument, temporary)
            } else {
                rawArgument.transform(this, null)
            }

            val builder = irBuilder(expression)
            // Resolve addUnsafe from the called declaration's class (SqlBuilder), because the
            // receiver's static type may be a type parameter, which has no class.
            val sqlBuilderClass = expression.symbol.owner.parentAsClass.symbol
            val addUnsafe = sqlBuilderClass.functions.first { it.owner.name.asString() == "addUnsafe" }
            builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                +temporary
                +irCall(addUnsafe).apply {
                    dispatchReceiver = irGet(temporary)
                    val addUnsafeParam = addUnsafe.owner.parameters.first { it.kind == IrParameterKind.Regular }
                    arguments[addUnsafeParam] = sqlArgument
                }
            }
        } finally {
            current = previous
        }
    }

    override fun visitStringConcatenation(expression: IrStringConcatenation): IrExpression {
        val enclosing = current ?: return super.visitStringConcatenation(expression)

        // The template's values are bound as parameters, not SQL text, so transform them outside
        // the enclosing add/unaryPlus context: a nested add/unaryPlus inside a value must still be
        // rewritten (it sets up its own receiver), while a string template inside a value stays a
        // plain concatenation whose evaluated result is bound as a single value.
        withoutCurrent {
            expression.transformChildren(this, null)
        }

        val (fragments, values) = StringConcatenationProcessor.process(expression.arguments)
        return irInterpolateCall(irBuilder(expression), enclosing, fragments, values)
    }

    /**
     * Applies the auto-trimIndent semantics to the whole add/unaryPlus argument, exactly once:
     * - a string template is trim-indented at compile time via [TrimFolding] (zero runtime cost);
     *   templates *nested* inside the argument (e.g. in if/when branches) are deliberately not
     *   folded — the runtime wrap below covers them, and folding them too would apply trimIndent
     *   twice, which is not idempotent when blank leading/trailing lines remain
     * - a constant string is trim-indented directly
     * - anything else keeps its normal rewrite and is wrapped in a runtime `trimIndent()` call,
     *   which is safe on placeholder-interpolated strings because `:pN` contains no whitespace
     */
    private fun autoTrimmedArgument(
        argument: IrExpression,
        enclosing: IrVariable,
    ): IrExpression = when {
        argument is IrStringConcatenation ->
            foldedTemplateOrNull(argument, enclosing)
                // The sentinel-collision fallback (a NUL character inside the SQL text)
                ?: irRuntimeTrimIndent(argument.transform(this, null))
        argument is IrConst && argument.kind == IrConstKind.String ->
            irBuilder(argument).irString((argument.value as String).trimIndent())
        else -> irRuntimeTrimIndent(argument.transform(this, null))
    }

    private fun foldedTemplateOrNull(
        template: IrStringConcatenation,
        enclosing: IrVariable,
    ): IrExpression? {
        // Check foldability on the arguments *before* transforming them, so that on fallback the
        // IR is left completely untouched. The IrConst-or-not classification that `process`
        // relies on is invariant under the transformation.
        val (fragments, rawValues) = StringConcatenationProcessor.process(template.arguments)
        val folded = TrimFolding.foldOrNull(fragments, rawValues.size) ?: return null
        // Same reasoning as in visitStringConcatenation: values are bound as parameters, so
        // transform them outside the enclosing add/unaryPlus context.
        val values = withoutCurrent { rawValues.map { it.transform(this, null) } }
        return irInterpolateCall(irBuilder(template), enclosing, folded, values)
    }

    private fun irRuntimeTrimIndent(argument: IrExpression): IrExpression {
        val trimIndent = pluginContext.trimIndentRef()
        return irBuilder(argument).irCall(trimIndent).apply {
            val receiverParam = trimIndent.owner.parameters.first { it.kind == IrParameterKind.ExtensionReceiver }
            arguments[receiverParam] = argument
        }
    }

    // Transform an expression outside the enclosing add/unaryPlus context (see
    // visitStringConcatenation for why values must be transformed without it).
    private inline fun <T> withoutCurrent(block: () -> T): T {
        val previous = current
        current = null
        return try {
            block()
        } finally {
            current = previous
        }
    }

    private fun irInterpolateCall(
        builder: DeclarationIrBuilder,
        enclosing: IrVariable,
        fragments: List<String>,
        values: List<IrExpression>,
    ): IrExpression {
        val defaultSqlBuilderClass =
            checkNotNull(pluginContext.finderForBuiltins().findClass(ClassIds.DEFAULT_SQL_BUILDER))
        val interpolate = defaultSqlBuilderClass.functions.first { it.owner.name.asString() == "interpolate" }

        return builder.irCall(interpolate).apply {
            dispatchReceiver = builder.irCastIfNeeded(
                builder.irGet(enclosing),
                defaultSqlBuilderClass.typeWith(),
            )
            val regularParams = interpolate.owner.parameters.filter { it.kind == IrParameterKind.Regular }
            arguments[regularParams[0]] =
                builder.irListOf(pluginContext.irBuiltIns.stringType, fragments.map { builder.irString(it) })
            arguments[regularParams[1]] = builder.irListOf(pluginContext.irBuiltIns.anyType, values)
        }
    }

    private fun irBuilder(expression: IrExpression): DeclarationIrBuilder = DeclarationIrBuilder(
        pluginContext,
        checkNotNull(currentScope).scope.scopeOwnerSymbol,
        expression.startOffset,
        expression.endOffset,
    )

    private fun DeclarationIrBuilder.irListOf(
        type: IrType,
        values: List<IrExpression>,
    ): IrExpression {
        val vararg = irVararg(type, values)
        val ref = pluginContext.listOfRef()
        return irCall(ref).apply {
            val listOfParam = ref.owner.parameters.first { it.kind == IrParameterKind.Regular }
            arguments[listOfParam] = vararg
        }
    }

    companion object {
        // Identify the call by its resolved declaration, not by the receiver expression's static
        // type: the receiver may be typed as a type parameter bounded by SqlBuilder (e.g. a fluent
        // helper `fun <T : SqlBuilder> T.helper(): T`), and the rewrite must still apply.
        private fun IrCall.isAddOrUnaryPlus(): Boolean = when (symbol.owner.name.asString()) {
            "add", "unaryPlus" ->
                symbol.owner.parentClassOrNull?.fqNameWhenAvailable?.asString() == ClassNames.SQL_BUILDER
            else -> false
        }

        private fun IrPluginContext.listOfRef(): IrSimpleFunctionSymbol =
            finderForBuiltins().findFunctions(CallableIds.LIST_OF)
                .first {
                    it.owner.parameters.firstOrNull { p -> p.kind == IrParameterKind.Regular }?.isVararg ?: false
                }

        private fun IrPluginContext.trimIndentRef(): IrSimpleFunctionSymbol =
            finderForBuiltins().findFunctions(CallableIds.TRIM_INDENT).single()
    }
}
