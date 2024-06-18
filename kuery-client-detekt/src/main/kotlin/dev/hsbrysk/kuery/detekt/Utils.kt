package dev.hsbrysk.kuery.detekt

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtUnaryExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull

internal const val ADD_FQ_NAME = "dev.hsbrysk.kuery.core.SqlBuilder.add"

internal const val UNARY_PLUS_FQ_NAME = "dev.hsbrysk.kuery.core.SqlBuilder.unaryPlus"

internal tailrec fun getLastReceiverExpression(expression: KtDotQualifiedExpression): KtExpression {
    val dotQualifiedExpression = expression.receiverExpression as? KtDotQualifiedExpression
        ?: return expression.receiverExpression
    return getLastReceiverExpression(dotQualifiedExpression)
}

internal fun isSqlBuilderAddExpression(
    expression: KtCallExpression,
    bindingContext: BindingContext,
): Boolean {
    val maybeAdd = expression.calleeExpression?.text == "add" &&
        expression.valueArguments.size == 1
    if (!maybeAdd) {
        return false
    }

    val callFqName = expression.getResolvedCall(bindingContext)?.resultingDescriptor?.fqNameOrNull()
    return callFqName?.asString() == ADD_FQ_NAME
}

internal fun isSqlBuilderUnaryExpression(
    expression: KtUnaryExpression,
    bindingContext: BindingContext,
): Boolean {
    val unaryPlusFqName = expression.getResolvedCall(bindingContext)?.resultingDescriptor?.fqNameOrNull()
    return unaryPlusFqName?.asString() == UNARY_PLUS_FQ_NAME
}
