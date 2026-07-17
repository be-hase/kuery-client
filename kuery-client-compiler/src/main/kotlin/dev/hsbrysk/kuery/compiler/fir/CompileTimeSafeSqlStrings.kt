package dev.hsbrysk.kuery.compiler.fir

import dev.hsbrysk.kuery.compiler.misc.CallableIds
import org.jetbrains.kotlin.fir.declarations.utils.isConst
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirStringConcatenationCall
import org.jetbrains.kotlin.fir.expressions.FirWhenBranch
import org.jetbrains.kotlin.fir.expressions.FirWhenExpression
import org.jetbrains.kotlin.fir.expressions.unwrapArgument
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.types.isNothing
import org.jetbrains.kotlin.fir.types.resolvedType

/**
 * The single definition of a "compile-time-safe" SQL string shape, shared by every FIR checker
 * so their notions cannot drift: a string whose text is fully determined at compile time (and
 * whose interpolation is therefore converted into bind parameters by
 * [dev.hsbrysk.kuery.compiler.ir.StringInterpolationTransformer]).
 */
internal object CompileTimeSafeSqlStrings {
    val ALLOWED_CHAIN_METHODS = setOf(CallableIds.TRIM_INDENT, CallableIds.TRIM_MARGIN)

    fun isCompileTimeSafe(expression: FirExpression): Boolean = when (expression) {
        // "SELECT ..."
        is FirLiteralExpression -> true
        // "SELECT ... $id" (converted into bind parameters by the IR transformation)
        is FirStringConcatenationCall -> true
        // reference to a const val (a compile-time constant)
        is FirPropertyAccessExpression -> expression.calleeReference.toResolvedCallableSymbol()?.isConst == true
        // "...".trimIndent() / "...".trimMargin(marginPrefix = <literal>)
        is FirFunctionCall ->
            expression.calleeReference.toResolvedCallableSymbol()?.callableId in ALLOWED_CHAIN_METHODS &&
                expression.argumentList.arguments.all { it.unwrapArgument() is FirLiteralExpression } &&
                expression.extensionReceiver?.let(::isCompileTimeSafe) == true
        // if / when whose every branch results in a compile-time-safe expression;
        // a Nothing-typed branch (throw / error(...) etc.) never yields a SQL string, so it is vacuously safe
        is FirWhenExpression ->
            expression.branches.all { branch ->
                branch.resultExpressionOrNull()
                    ?.let { it.resolvedType.isNothing || isCompileTimeSafe(it) } == true
            }
        else -> false
    }
}

// The value a when/if branch evaluates to (the last statement of its block), if any.
internal fun FirWhenBranch.resultExpressionOrNull(): FirExpression? = result.statements.lastOrNull() as? FirExpression
