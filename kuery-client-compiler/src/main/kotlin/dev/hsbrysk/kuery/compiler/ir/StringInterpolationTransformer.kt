package dev.hsbrysk.kuery.compiler.ir

import dev.hsbrysk.kuery.compiler.ir.misc.CallableIds
import dev.hsbrysk.kuery.compiler.ir.misc.ClassIds
import dev.hsbrysk.kuery.compiler.ir.misc.ClassNames
import dev.hsbrysk.kuery.compiler.ir.misc.StringConcatenationProcessor
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStringConcatenation
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrFail
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.irCastIfNeeded
import org.jetbrains.kotlin.ir.util.isVararg

@Suppress("OPT_IN_USAGE")
class StringInterpolationTransformer(private val pluginContext: IrPluginContext) :
    IrElementTransformerVoidWithContext() {
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

            val sqlArgumentParameter = expression.symbol.owner.parameters.first {
                when (expression.symbol.owner.name.asString()) {
                    "add" -> it.kind == IrParameterKind.Regular
                    "unaryPlus" -> it.kind == IrParameterKind.ExtensionReceiver
                    else -> error("Unexpected error") // not happened
                }
            }
            val sqlArgument = checkNotNull(expression.arguments[sqlArgumentParameter.indexInParameters])
                .transform(this, null)

            val builder = irBuilder(expression)
            val sqlBuilderClass = temporary.type.classOrFail
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
        val current = current ?: return super.visitStringConcatenation(expression)
        val builder = irBuilder(expression)

        val (fragments, values) = StringConcatenationProcessor(builder).process(expression.arguments).let {
            Pair(
                builder.irListOf(pluginContext.irBuiltIns.stringType, it.first),
                builder.irListOf(pluginContext.irBuiltIns.anyType, it.second),
            )
        }

        val defaultSqlBuilderClass =
            checkNotNull(pluginContext.finderForBuiltins().findClass(ClassIds.DEFAULT_SQL_BUILDER))
        val interpolate = defaultSqlBuilderClass.functions.first { it.owner.name.asString() == "interpolate" }

        return builder.irCall(interpolate).apply {
            dispatchReceiver = builder.irCastIfNeeded(
                builder.irGet(current),
                defaultSqlBuilderClass.typeWith(),
            )
            val regularParams = interpolate.owner.parameters.filter { it.kind == IrParameterKind.Regular }
            arguments[regularParams[0]] = fragments
            arguments[regularParams[1]] = values
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
        private fun IrCall.isAddOrUnaryPlus(): Boolean {
            if (dispatchReceiver?.type?.classFqName?.asString() != ClassNames.SQL_BUILDER) {
                return false
            }
            when (symbol.owner.name.asString()) {
                "add", "unaryPlus" -> return true
            }
            return false
        }

        private fun IrPluginContext.listOfRef(): IrSimpleFunctionSymbol =
            finderForBuiltins().findFunctions(CallableIds.LIST_OF)
                .first {
                    it.owner.parameters.firstOrNull { p -> p.kind == IrParameterKind.Regular }?.isVararg ?: false
                }
    }
}
