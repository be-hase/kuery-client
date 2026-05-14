package dev.hsbrysk.kuery.compiler.ir

import dev.hsbrysk.kuery.compiler.ir.misc.CallableIds
import dev.hsbrysk.kuery.compiler.ir.misc.ClassIds
import dev.hsbrysk.kuery.compiler.ir.misc.ClassNames
import dev.hsbrysk.kuery.compiler.ir.misc.StringConcatenationProcessor
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
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
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

@Suppress("OPT_IN_USAGE")
class StringInterpolationTransformer(private val pluginContext: IrPluginContext) : IrElementTransformerVoid() {
    private var current: IrCall? = null

    override fun visitCall(expression: IrCall): IrExpression {
        if (expression.isAddOrUnaryPlus()) {
            return try {
                current = expression
                super.visitCall(expression)

                when (expression.symbol.owner.name.asString()) {
                    "add" -> transformAddCall(expression)
                    "unaryPlus" -> transformUnaryPlusCall(expression)
                    else -> error("Unexpected error") // not happened
                }
            } finally {
                current = null
            }
        }

        return super.visitCall(expression)
    }

    private fun transformAddCall(expression: IrCall): IrCall {
        val builder = irBuilder(expression)
        val sqlBuilder = checkNotNull(expression.dispatchReceiver)
        val sqlBuilderClass = sqlBuilder.type.classOrFail
        val addUnsafe = sqlBuilderClass.functions.first { it.owner.name.asString() == "addUnsafe" }
        return builder.irCall(addUnsafe).apply {
            dispatchReceiver = sqlBuilder
            val addUnsafeParam = addUnsafe.owner.parameters.first { it.kind == IrParameterKind.Regular }
            val sqlArgument = expression.arguments[
                expression.symbol.owner.parameters.first { it.kind == IrParameterKind.Regular }.indexInParameters,
            ]
            arguments[addUnsafeParam] = sqlArgument
        }
    }

    private fun transformUnaryPlusCall(expression: IrCall): IrCall {
        val builder = irBuilder(expression)
        val sqlBuilder = checkNotNull(expression.dispatchReceiver)
        val sqlBuilderClass = sqlBuilder.type.classOrFail
        val addUnsafe = sqlBuilderClass.functions.first { it.owner.name.asString() == "addUnsafe" }
        return builder.irCall(addUnsafe).apply {
            dispatchReceiver = sqlBuilder
            val addUnsafeParam = addUnsafe.owner.parameters.first { it.kind == IrParameterKind.Regular }
            val extensionReceiverValue = expression.arguments[
                expression.symbol.owner.parameters.first {
                    it.kind == IrParameterKind.ExtensionReceiver
                }.indexInParameters,
            ]
            arguments[addUnsafeParam] = extensionReceiverValue
        }
    }

    override fun visitStringConcatenation(expression: IrStringConcatenation): IrExpression {
        val current = current ?: return super.visitStringConcatenation(expression)
        val builder = irBuilder(current)

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
                checkNotNull(current.dispatchReceiver),
                defaultSqlBuilderClass.typeWith(),
            )
            val regularParams = interpolate.owner.parameters.filter { it.kind == IrParameterKind.Regular }
            arguments[regularParams[0]] = fragments
            arguments[regularParams[1]] = values
        }
    }

    private fun irBuilder(expression: IrCall): DeclarationIrBuilder = DeclarationIrBuilder(
        pluginContext,
        expression.symbol,
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
