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
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtUnaryExpression

@Suppress("NestedBlockDepth")
class UseStringLiteralRule(config: Config) : Rule(config) {
    override val issue = Issue(
        id = "UseStringLiteral",
        severity = Severity.Warning,
        description = "To keep it concise, use String Literal.",
        debt = Debt.FIVE_MINS,
    )

    private val allowRegexes = buildList {
        addAll(valueOrNull<List<String>>("allowRegexes")?.map { it.toRegex() }.orEmpty())
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        if (isSqlBuilderAddExpression(expression, bindingContext)) {
            val argExpression = expression.valueArguments.first().getArgumentExpression()
            if (!isValidExpression(argExpression)) {
                if (!allowByRegexes(argExpression)) {
                    report(
                        CodeSmell(
                            issue = issue,
                            entity = Entity.from(expression),
                            message = """
                            To keep it concise, use String Literal.
                            """.trimIndent(),
                        ),
                    )
                }
            }
        }
    }

    override fun visitUnaryExpression(expression: KtUnaryExpression) {
        super.visitUnaryExpression(expression)

        if (isSqlBuilderUnaryExpression(expression, bindingContext)) {
            val baseExpression = expression.baseExpression
            if (!isValidExpression(baseExpression)) {
                if (!allowByRegexes(baseExpression)) {
                    report(
                        CodeSmell(
                            issue = issue,
                            entity = Entity.from(expression),
                            message = """
                            To keep it concise, use String Literal.
                            """.trimIndent(),
                        ),
                    )
                }
            }
        }
    }

    private fun isValidExpression(expression: KtExpression?): Boolean = if (expression is KtStringTemplateExpression) {
        true
    } else if (
        expression is KtDotQualifiedExpression &&
        getLastReceiverExpression(expression) is KtStringTemplateExpression
    ) {
        true
    } else {
        false
    }

    private fun allowByRegexes(expression: KtExpression?): Boolean {
        expression ?: return false
        if (allowRegexes.isEmpty()) {
            return false
        }
        return allowRegexes.any { expression.text.contains(it) }
    }
}
