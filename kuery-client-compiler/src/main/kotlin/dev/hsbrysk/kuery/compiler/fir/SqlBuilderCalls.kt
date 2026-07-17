package dev.hsbrysk.kuery.compiler.fir

import dev.hsbrysk.kuery.compiler.misc.ClassIds
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.unwrapArgument
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name

internal object SqlBuilderCalls {
    private val ADD = CallableId(ClassIds.SQL_BUILDER, Name.identifier("add"))
    private val UNARY_PLUS = CallableId(ClassIds.SQL_BUILDER, Name.identifier("unaryPlus"))
    val BIND = CallableId(ClassIds.SQL_BUILDER, Name.identifier("bind"))

    /**
     * The SQL string argument if [call] is `SqlBuilder.add` / `String.unaryPlus` (the calls processed by
     * [dev.hsbrysk.kuery.compiler.ir.StringInterpolationTransformer]), otherwise null.
     */
    fun sqlArgumentOrNull(call: FirFunctionCall): FirExpression? =
        when (call.calleeReference.toResolvedCallableSymbol()?.callableId) {
            ADD -> call.argumentList.arguments.firstOrNull()?.unwrapArgument()
            UNARY_PLUS -> call.extensionReceiver
            else -> null
        }
}
