package dev.hsbrysk.kuery.detekt.rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtStringTemplateEntryWithExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtUnaryExpression

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
        if (isTargetCallExpression(expression)) {
            if (isInSqlCallExpression(expression)) {
                val stringTemplate =
                    expression.valueArguments.first().getArgumentExpression() as KtStringTemplateExpression
                val hasViolation = hasViolation(stringTemplate)
                if (hasViolation) {
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
    }

    override fun visitUnaryExpression(expression: KtUnaryExpression) {
        super.visitUnaryExpression(expression)
        if (isTargetUnaryExpression(expression)) {
            if (isInSqlCallExpression(expression)) {
                val stringTemplate = expression.baseExpression as KtStringTemplateExpression
                val hasViolation = hasViolation(stringTemplate)
                if (hasViolation) {
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
    }

    private fun isTargetCallExpression(expression: KtCallExpression): Boolean {
        return expression.calleeExpression?.text == "add" &&
            expression.valueArguments.size == 1 &&
            expression.valueArguments.first().getArgumentExpression() is KtStringTemplateExpression
    }

    private fun isTargetUnaryExpression(expression: KtUnaryExpression): Boolean {
        return expression.baseExpression is KtStringTemplateExpression
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
