package dev.hsbrysk.kuery.detekt

import dev.hsbrysk.kuery.detekt.rules.StringInterpolationRule
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

class KueryClientRuleSetProvider : RuleSetProvider {
    override val ruleSetId: String = "kuery-client"

    override fun instance(config: Config): RuleSet {
        return RuleSet(
            ruleSetId,
            listOf(
                StringInterpolationRule(config),
            ),
        )
    }
}
