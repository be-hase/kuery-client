package dev.hsbrysk.kuery.detekt

import dev.hsbrysk.kuery.detekt.rules.UseStringLiteralRule
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

class KueryClientRuleSetProvider : RuleSetProvider {
    override val ruleSetId: String = "kuery-client"

    override fun instance(config: Config): RuleSet = RuleSet(
        ruleSetId,
        listOf(
            UseStringLiteralRule(config),
        ),
    )
}
