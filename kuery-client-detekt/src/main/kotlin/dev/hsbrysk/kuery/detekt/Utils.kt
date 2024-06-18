package dev.hsbrysk.kuery.detekt

import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression

internal const val ADD_FQ_NAME = "dev.hsbrysk.kuery.core.SqlBuilder.add"

internal const val UNARY_PLUS_FQ_NAME = "dev.hsbrysk.kuery.core.SqlBuilder.unaryPlus"

internal tailrec fun getLastReceiverExpression(expression: KtDotQualifiedExpression): KtExpression {
    val dotQualifiedExpression = expression.receiverExpression as? KtDotQualifiedExpression
        ?: return expression.receiverExpression
    return getLastReceiverExpression(dotQualifiedExpression)
}
