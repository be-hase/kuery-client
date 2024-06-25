package dev.hsbrysk.kuery.compiler.ir

import dev.hsbrysk.kuery.compiler.ir.misc.CallableIds
import dev.hsbrysk.kuery.compiler.ir.misc.ClassIds
import dev.hsbrysk.kuery.compiler.ir.misc.ClassNames
import dev.hsbrysk.kuery.compiler.ir.misc.StringConcatenationProcessor
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
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
import org.jetbrains.kotlin.ir.util.isVararg
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

class StringInterpolationTransformer(private val pluginContext: IrPluginContext) : IrElementTransformerVoid() {
    private var current: IrCall? = null

    override fun visitCall(expression: IrCall): IrExpression {
        if (!expression.isTarget()) {
            return super.visitCall(expression)
        }
        return try {
            current = expression
            super.visitCall(expression)
        } finally {
            current = null
        }
    }

    override fun visitStringConcatenation(expression: IrStringConcatenation): IrExpression {
        val current = current ?: return super.visitStringConcatenation(expression)

        val builder = DeclarationIrBuilder(
            pluginContext,
            current.symbol,
            current.startOffset,
            current.endOffset,
        )

        val (fragments, values) = StringConcatenationProcessor(builder).process(expression.arguments).let {
            Pair(
                builder.irListOf(pluginContext.symbols.string.defaultType, it.first),
                builder.irListOf(pluginContext.symbols.any.defaultType, it.second),
            )
        }
        println(fragments)
        println(values)

        val defaultSqlBuilderClass = checkNotNull(pluginContext.referenceClass(ClassIds.DEFAULT_SQL_BUILDER))
        val interpolate = defaultSqlBuilderClass.functions.first { it.owner.name.asString() == "interpolate" }

        return builder.irCall(interpolate, pluginContext.symbols.string.defaultType).apply {
            dispatchReceiver = current.dispatchReceiver
            putValueArgument(0, fragments)
            putValueArgument(1, values)
        }
    }

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
        private fun IrCall.isTarget(): Boolean {
            if (dispatchReceiver?.type?.classFqName?.asString() != ClassNames.SQL_BUILDER) {
                return false
            }
            when (symbol.owner.name.asString()) {
                "unaryPlus", "add" -> return true
            }
            return true
        }

        private fun IrPluginContext.listOfRef(): IrSimpleFunctionSymbol {
            return referenceFunctions(CallableIds.LIST_OF)
                .first { it.owner.valueParameters.firstOrNull()?.isVararg ?: false }
        }
    }
}
