package dev.hsbrysk.kuery.compiler.ir

import dev.hsbrysk.kuery.compiler.ir.misc.CallableIds
import dev.hsbrysk.kuery.compiler.ir.misc.ClassIds
import dev.hsbrysk.kuery.compiler.ir.misc.ClassNames
import dev.hsbrysk.kuery.compiler.ir.misc.StringConcatenationProcessor
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStringConcatenation
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.irCastIfNeeded
import org.jetbrains.kotlin.ir.util.isVararg
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

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
        val defaultSqlBuilderClass = defaultSqlBuilderClass()
        val addInternal = defaultSqlBuilderClass.functions.first { it.owner.name.asString() == "addInternal" }
        return builder.irCall(addInternal, pluginContext.symbols.unit.defaultType).apply {
            dispatchReceiver = builder.irCastIfNeeded(
                checkNotNull(expression.dispatchReceiver),
                defaultSqlBuilderClass.typeWith(),
            )
            putValueArgument(0, expression.valueArguments.first())
        }
    }

    private fun transformUnaryPlusCall(expression: IrCall): IrCall {
        val builder = irBuilder(expression)
        val defaultSqlBuilderClass = defaultSqlBuilderClass()
        val addInternal = defaultSqlBuilderClass.functions.first { it.owner.name.asString() == "addInternal" }
        return builder.irCall(addInternal, pluginContext.symbols.unit.defaultType).apply {
            dispatchReceiver = builder.irCastIfNeeded(
                checkNotNull(expression.dispatchReceiver),
                defaultSqlBuilderClass.typeWith(),
            )
            putValueArgument(0, expression.extensionReceiver)
        }
    }

    override fun visitStringConcatenation(expression: IrStringConcatenation): IrExpression {
        val current = current ?: return super.visitStringConcatenation(expression)
        val builder = irBuilder(current)

        val (fragments, values) = StringConcatenationProcessor(builder).process(expression.arguments).let {
            Pair(
                builder.irListOf(pluginContext.symbols.string.defaultType, it.first),
                builder.irListOf(pluginContext.symbols.any.defaultType, it.second),
            )
        }

        val defaultSqlBuilderClass = defaultSqlBuilderClass()
        val interpolate = defaultSqlBuilderClass.functions.first { it.owner.name.asString() == "interpolate" }

        return builder.irCall(interpolate, pluginContext.symbols.string.defaultType).apply {
            dispatchReceiver = builder.irCastIfNeeded(
                checkNotNull(current.dispatchReceiver),
                defaultSqlBuilderClass.typeWith(),
            )
            putValueArgument(0, fragments)
            putValueArgument(1, values)
        }
    }

    private fun irBuilder(expression: IrCall): DeclarationIrBuilder {
        return DeclarationIrBuilder(
            pluginContext,
            expression.symbol,
            expression.startOffset,
            expression.endOffset,
        )
    }

    private fun defaultSqlBuilderClass() = checkNotNull(pluginContext.referenceClass(ClassIds.DEFAULT_SQL_BUILDER))

    private fun IrBuilderWithScope.irListOf(
        type: IrType,
        values: List<IrExpression>,
    ): IrExpression {
        val vararg = irVararg(type, values)
        return irCall(pluginContext.listOfRef(), pluginContext.symbols.list.typeWith(type)).apply {
            putTypeArgument(0, type)
            putValueArgument(0, vararg)
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
            return true
        }

        private fun IrPluginContext.listOfRef(): IrSimpleFunctionSymbol {
            return referenceFunctions(CallableIds.LIST_OF)
                .first { it.owner.valueParameters.firstOrNull()?.isVararg ?: false }
        }
    }
}
