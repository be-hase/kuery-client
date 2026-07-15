package dev.hsbrysk.kuery.compiler.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.declarations.utils.isConst
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirStringConcatenationCall
import org.jetbrains.kotlin.fir.expressions.FirWhenExpression
import org.jetbrains.kotlin.fir.expressions.unwrapArgument
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.types.isNothing
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal object UnsafeSqlStringChecker : FirFunctionCallChecker(MppCheckerKind.Common) {
    private val ALLOWED_CHAIN_METHODS = setOf(
        CallableId(FqName("kotlin.text"), Name.identifier("trimIndent")),
        CallableId(FqName("kotlin.text"), Name.identifier("trimMargin")),
    )

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        val sqlArgument = SqlBuilderCalls.sqlArgumentOrNull(expression) ?: return
        if (isCompileTimeSafe(sqlArgument)) {
            return
        }
        reporter.reportOn(sqlArgument.source ?: expression.source, KueryClientDiagnostics.KUERY_UNSAFE_SQL_STRING)
    }

    /**
     * Whether the SQL string is fully determined at compile time (and therefore any string interpolation
     * in it is converted into bind parameters by [dev.hsbrysk.kuery.compiler.ir.StringInterpolationTransformer]).
     */
    private fun isCompileTimeSafe(expression: FirExpression): Boolean = when (expression) {
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
                (branch.result.statements.lastOrNull() as? FirExpression)
                    ?.let { it.resolvedType.isNothing || isCompileTimeSafe(it) } == true
            }
        else -> false
    }
}
