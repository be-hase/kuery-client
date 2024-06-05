package dev.hsbrysk.kuery.detekt.rules

import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.psiUtil.parents

internal fun isInSqlCallExpression(expression: PsiElement): Boolean {
    return expression.parents
        .filter { it is KtCallExpression }
        .map { it as KtCallExpression }
        .filter { it.calleeExpression?.text == "sql" }
        .filter { it.valueArguments.size == 1 && it.lambdaArguments.size == 1 }
        .any()
}
