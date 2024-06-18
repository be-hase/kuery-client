package dev.hsbrysk.kuery.detekt.rules

import dev.hsbrysk.kuery.detekt.getLastReceiverExpression
import dev.hsbrysk.kuery.detekt.isSqlBuilderAddExpression
import dev.hsbrysk.kuery.detekt.isSqlBuilderUnaryExpression
import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.api.internal.RequiresTypeResolution
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtStringTemplateEntryWithExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtUnaryExpression

@RequiresTypeResolution
class StringInterpolationRule(config: Config) : Rule(config) {
    override val issue = Issue(
        id = "StringInterpolation",
        severity = Severity.Security,
        description = "Detects string interpolation in KueryClient without using bind, ...etc. ",
        debt = Debt.FIVE_MINS,
    )

    private val allowRegexes = buildList {
        add(BIND_REGEX)
        addAll(valueOrNull<List<String>>("allowRegexes")?.map { it.toRegex() }.orEmpty())
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        if (isSqlBuilderAddExpression(expression, bindingContext)) {
            val argExpression = expression.valueArguments.first().getArgumentExpression()
            val stringTemplate = getStringTemplateExpressionOrNull(argExpression) ?: return
            if (hasViolation(stringTemplate)) {
                report(
                    CodeSmell(
                        issue = issue,
                        entity = Entity.from(expression),
                        message = """
                        Use bind and similar methods to safely perform string interpolation.
                        """.trimIndent(),
                    ),
                )
            }
        }
    }

    override fun visitUnaryExpression(expression: KtUnaryExpression) {
        super.visitUnaryExpression(expression)

        if (isSqlBuilderUnaryExpression(expression, bindingContext)) {
            val stringTemplate = getStringTemplateExpressionOrNull(expression.baseExpression) ?: return
            if (hasViolation(stringTemplate)) {
                report(
                    CodeSmell(
                        issue = issue,
                        entity = Entity.from(expression),
                        message = """
                        Use bind and similar methods to safely perform string interpolation.
                        """.trimIndent(),
                    ),
                )
            }
        }
    }

    private fun getStringTemplateExpressionOrNull(expression: KtExpression?): KtStringTemplateExpression? {
        if (expression is KtStringTemplateExpression) {
            return expression
        }
        if (expression is KtDotQualifiedExpression) {
            val lastReceiverExpression = getLastReceiverExpression(expression)
            if (lastReceiverExpression is KtStringTemplateExpression) {
                return lastReceiverExpression
            }
        }
        return null
    }

    private fun hasViolation(expression: KtStringTemplateExpression): Boolean {
        val texts = expression.entries
            .mapNotNull { it as? KtStringTemplateEntryWithExpression }
            .mapNotNull { it.expression?.text }
        return texts
            .filterNot { text ->
                allowRegexes.any { text.contains(it) }
            }
            .any()
    }

    companion object {
        val BIND_REGEX = "^bind\\(.+\\)$".toRegex()
    }
}
