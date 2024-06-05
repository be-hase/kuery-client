package dev.hsbrysk.kuery.detekt.rules

import assertk.assertThat
import assertk.assertions.hasSize
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Test

class UseStringLiteralRuleTest {
    private val rule = UseStringLiteralRule(
        TestConfig("allowRegexes" to listOf("^hoge\\(.*\\)$")),
    )

    @Test
    fun `add - OK pattern1`() {
        val code = """
            val client: KueryClient = KueryClient()
            fun main() {
                client.sql {
                    add("SELECT * FROM user WHERE id = ${'$'}{bind(id)}")
                }
            }
        """.trimIndent()

        val findings = rule.lint(code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `add - OK pattern2`() {
        val code = """
            val client: KueryClient = KueryClient()
            fun main() {
                client.sql {
                    fun hoge(obj: Any): String = ""
                    add(hoge("bar"))
                }
            }
        """.trimIndent()

        val findings = rule.lint(code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `add - NG pattern1`() {
        val code = """
            val client: KueryClient = KueryClient()
            fun main() {
                client.sql {
                    val sql = "SELECT * FROM user WHERE id = ${'$'}{bind(id)}"
                    add(sql)
                }
            }
        """.trimIndent()

        val findings = rule.lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `unaryPlus - OK pattern1`() {
        val code = """
            val client: KueryClient = KueryClient()
            fun main() {
                client.sql {
                    +"SELECT * FROM user WHERE id = ${'$'}{bind(id)}"
                }
            }
        """.trimIndent()

        val findings = rule.lint(code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `unaryPlus - OK pattern2`() {
        val code = """
            val client: KueryClient = KueryClient()
            fun main() {
                client.sql {
                    fun hoge(obj: Any): String = ""
                    +hoge("bar")
                }
            }
        """.trimIndent()

        val findings = rule.lint(code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `unaryPlus - NG pattern1`() {
        val code = """
            val client: KueryClient = KueryClient()
            fun main() {
                client.sql {
                    val sql = "SELECT * FROM user WHERE id = ${'$'}{bind(id)}"
                    +sql
                }
            }
        """.trimIndent()

        val findings = rule.lint(code)
        assertThat(findings).hasSize(1)
    }
}
