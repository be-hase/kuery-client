package dev.hsbrysk.kuery.compiler.ir

import dev.hsbrysk.kuery.compiler.ir.misc.CallableIds
import dev.hsbrysk.kuery.compiler.ir.misc.ClassIds
import dev.hsbrysk.kuery.compiler.ir.misc.ClassNames
import dev.hsbrysk.kuery.compiler.ir.misc.StringConcatenationProcessor
import dev.hsbrysk.kuery.compiler.ir.misc.TrimFolding
import dev.hsbrysk.kuery.compiler.ir.misc.TrimOperation
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
class StringInterpolationTransformer(private val pluginContext: IrPluginContext) :
    IrElementTransformerVoidWithContext() {
    // The SqlBuilder receiver of the enclosing add/unaryPlus call, hoisted into a temporary
    // variable so that both the addUnsafe call and the interpolate call can reference it
    // without sharing (and thus double-evaluating) the original receiver expression.
    private var current: IrVariable? = null

    override fun visitCall(expression: IrCall): IrExpression {
        if (!expression.isAddOrUnaryPlus()) {
            return foldTrimCallOrNull(expression) ?: super.visitCall(expression)
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
            val sqlArgument = checkNotNull(expression.arguments[sqlArgumentParameter.indexInParameters])
                .transform(this, null)

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
     * Folds a `trimIndent()` / `trimMargin(...)` call on a SQL string inside an add/unaryPlus
     * argument at compile time, so no trim runs at runtime on the placeholder-interpolated
     * string. Returns null when the call is not such a trim or when equivalence cannot be
     * guaranteed (non-literal receiver or margin prefix, prefix that could match into `:pN`,
     * etc.), in which case the caller leaves the call in place and it trims at runtime as
     * before.
     */
    private fun foldTrimCallOrNull(expression: IrCall): IrExpression? {
        val enclosing = current ?: return null
        val operation = expression.trimOperationOrNull() ?: return null
        val receiverParameter = expression.symbol.owner.parameters.firstOrNull {
            it.kind == IrParameterKind.ExtensionReceiver
        } ?: return null
        return when (val receiver = expression.arguments[receiverParameter.indexInParameters]) {
            is IrConst -> foldConstReceiverOrNull(expression, receiver, operation)
            is IrStringConcatenation -> foldTemplateReceiverOrNull(expression, receiver, operation, enclosing)
            else -> null
        }
    }

    private fun foldConstReceiverOrNull(
        expression: IrCall,
        receiver: IrConst,
        operation: TrimOperation,
    ): IrExpression? {
        if (receiver.kind != IrConstKind.String) {
            return null
        }
        // A constant receiver has no values to bind; apply the trim directly. A failing trim
        // (blank trimMargin prefix) must keep throwing at runtime, so fall back.
        return runCatching { operation.apply(receiver.value as String) }
            .getOrNull()
            ?.let { irBuilder(expression).irString(it) }
    }

    private fun foldTemplateReceiverOrNull(
        expression: IrCall,
        receiver: IrStringConcatenation,
        operation: TrimOperation,
        enclosing: IrVariable,
    ): IrExpression? {
        // Check foldability on the arguments *before* transforming them, so that on fallback the
        // IR is left completely untouched. The IrConst-or-not classification that `process`
        // relies on is invariant under the transformation.
        val (fragments, rawValues) = StringConcatenationProcessor.process(receiver.arguments)
        val folded = TrimFolding.foldOrNull(fragments, rawValues.size, operation) ?: return null
        // Same reasoning as in visitStringConcatenation: values are bound as parameters, so
        // transform them outside the enclosing add/unaryPlus context.
        val values = withoutCurrent { rawValues.map { it.transform(this, null) } }
        return irInterpolateCall(irBuilder(expression), enclosing, folded, values)
    }

    private fun IrCall.trimOperationOrNull(): TrimOperation? = when (symbol.owner.fqNameWhenAvailable) {
        TRIM_INDENT_FQ_NAME -> TrimOperation.TrimIndent
        TRIM_MARGIN_FQ_NAME -> {
            val prefixParameter = symbol.owner.parameters.first { it.kind == IrParameterKind.Regular }
            when (val prefix = arguments[prefixParameter.indexInParameters]) {
                // Omitted argument: the declaration's default, `"|"`.
                null -> TrimOperation.TrimMargin("|")
                is IrConst -> (prefix.value as? String)?.let { TrimOperation.TrimMargin(it) }
                else -> null
            }
        }
        else -> null
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
        private val TRIM_INDENT_FQ_NAME = CallableIds.TRIM_INDENT.asSingleFqName()
        private val TRIM_MARGIN_FQ_NAME = CallableIds.TRIM_MARGIN.asSingleFqName()

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
    }
}
