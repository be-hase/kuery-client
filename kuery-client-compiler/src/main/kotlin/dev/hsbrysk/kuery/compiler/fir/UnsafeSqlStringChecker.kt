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
import org.jetbrains.kotlin.fir.expressions.unwrapArgument
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal object UnsafeSqlStringChecker : FirFunctionCallChecker(MppCheckerKind.Common) {
    private val SQL_BUILDER_CLASS_ID = ClassId(FqName("dev.hsbrysk.kuery.core"), Name.identifier("SqlBuilder"))
    private val ADD = CallableId(SQL_BUILDER_CLASS_ID, Name.identifier("add"))
    private val UNARY_PLUS = CallableId(SQL_BUILDER_CLASS_ID, Name.identifier("unaryPlus"))
    private val ALLOWED_CHAIN_METHODS = setOf(
        CallableId(FqName("kotlin.text"), Name.identifier("trimIndent")),
        CallableId(FqName("kotlin.text"), Name.identifier("trimMargin")),
    )

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        val sqlArgument = when (expression.calleeReference.toResolvedCallableSymbol()?.callableId) {
            ADD -> expression.argumentList.arguments.firstOrNull()?.unwrapArgument() ?: return
            UNARY_PLUS -> expression.extensionReceiver ?: return
            else -> return
        }
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
        else -> false
    }
}
