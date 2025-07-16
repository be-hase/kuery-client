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
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStringConcatenation
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrFail
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.irCastIfNeeded
import org.jetbrains.kotlin.ir.util.isVararg
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import kotlin.collections.filter

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
        return builder.irCall(addUnsafe, pluginContext.symbols.unit.defaultType).apply {
            dispatchReceiver = sqlBuilder
            arguments[valueParameters().first()] =
                expression.arguments[expression.valueParameters().first()]
        }
    }

    private fun transformUnaryPlusCall(expression: IrCall): IrCall {
        val builder = irBuilder(expression)
        val sqlBuilder = checkNotNull(expression.dispatchReceiver)
        val sqlBuilderClass = sqlBuilder.type.classOrFail
        val addUnsafe = sqlBuilderClass.functions.first { it.owner.name.asString() == "addUnsafe" }
        return builder.irCall(addUnsafe, pluginContext.symbols.unit.defaultType).apply {
            dispatchReceiver = sqlBuilder
            arguments[valueParameters().first()] = expression.arguments[expression.extensionReceiver()]
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

        val defaultSqlBuilderClass = checkNotNull(pluginContext.referenceClass(ClassIds.DEFAULT_SQL_BUILDER))
        val interpolate = defaultSqlBuilderClass.functions.first { it.owner.name.asString() == "interpolate" }

        return builder.irCall(interpolate, pluginContext.symbols.string.defaultType).apply {
            dispatchReceiver = builder.irCastIfNeeded(
                checkNotNull(current.dispatchReceiver),
                defaultSqlBuilderClass.typeWith(),
            )
            val valueParams = valueParameters()
            arguments[valueParams[0]] = fragments
            arguments[valueParams[1]] = values
        }
    }

    private fun irBuilder(expression: IrCall): DeclarationIrBuilder = DeclarationIrBuilder(
        pluginContext,
        expression.symbol,
        expression.startOffset,
        expression.endOffset,
    )

    private fun IrBuilderWithScope.irListOf(
        type: IrType,
        values: List<IrExpression>,
    ): IrExpression {
        val vararg = irVararg(type, values)
        return irCall(pluginContext.listOfRef(), pluginContext.symbols.list.typeWith(type)).apply {
            typeArguments[0] = type
            arguments[valueParameters().first()] = vararg
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

        private fun IrCall.valueParameters(): List<IrValueParameter> =
            symbol.owner.parameters.filter {
                it.kind == IrParameterKind.Regular || it.kind == IrParameterKind.Context
            }

        private fun IrCall.extensionReceiver(): IrValueParameter =
            symbol.owner.parameters.first { it.kind == IrParameterKind.ExtensionReceiver }

        private fun IrPluginContext.listOfRef(): IrSimpleFunctionSymbol = referenceFunctions(CallableIds.LIST_OF)
            .first {
                it.owner
                    .parameters
                    .firstOrNull { i -> i.kind == IrParameterKind.Regular || i.kind == IrParameterKind.Context }
                    ?.isVararg ?: false
            }
    }
}
