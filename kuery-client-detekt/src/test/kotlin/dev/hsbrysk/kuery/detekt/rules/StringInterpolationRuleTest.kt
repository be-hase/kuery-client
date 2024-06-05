package dev.hsbrysk.kuery.detekt.rules

import assertk.assertThat
import assertk.assertions.hasSize
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Test

class StringInterpolationRuleTest {
    private val rule = StringInterpolationRule(
        TestConfig("allowRegexes" to listOf("^hoge(.*)$")),
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
                    add("SELECT * FROM user WHERE id = ${'$'}{  bind(id)  }")
                }
            }
        """.trimIndent()

        val findings = rule.lint(code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `add - OK pattern3`() {
        val code = """
            val client: KueryClient = KueryClient()
            fun main() {
                client.sql {
                    add("SELECT * FROM user WHERE id = ${'$'}{bind(id)} AND status = ${'$'}{bind(status)}")
                }
            }
        """.trimIndent()

        val findings = rule.lint(code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `add - OK pattern4`() {
        val code = """
            val client: KueryClient = KueryClient()
            fun main() {
                val sql = "SELECT * FROM user WHERE id = ${'$'}id"
                client.sql {
                    add(sql)
                }
            }
        """.trimIndent()

        val findings = rule.lint(code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `add - OK pattern5`() {
        val code = """
            val client: KueryClient = KueryClient()
            fun main() {
                client.sql {
                    add("SELECT * FROM user WHERE id = ${'$'}{hoge(id)}")
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
                    add("SELECT * FROM user WHERE id = ${'$'}id")
                }
            }
        """.trimIndent()

        val findings = rule.lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `add - NG pattern2`() {
        val code = """
            val client: KueryClient = KueryClient()
            fun main() {
                client.sql {
                    add("SELECT * FROM user WHERE id = ${'$'}{id}")
                }
            }
        """.trimIndent()

        val findings = rule.lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `add - NG pattern3`() {
        val code = """
            val client: KueryClient = KueryClient()
            fun main() {
                client.sql {
                    add("SELECT * FROM user WHERE id = ${'$'}{if (hoge) bind(id1) else bind(id2)}")
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
                    +"SELECT * FROM user WHERE id = ${'$'}{ bind(id) }"
                }
            }
        """.trimIndent()

        val findings = rule.lint(code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `unaryPlus - OK pattern3`() {
        val code = """
            val client: KueryClient = KueryClient()
            fun main() {
                client.sql {
                    +"SELECT * FROM user WHERE id = ${'$'}{bind(id)} AND status = ${'$'}{bind(status)}"
                }
            }
        """.trimIndent()

        val findings = rule.lint(code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `unaryPlus - OK pattern4`() {
        // OKだが、別のruleに引っかかります
        val code = """
            val client: KueryClient = KueryClient()
            fun main() {
                val sql = "SELECT * FROM user WHERE id = ${'$'}id"
                client.sql {
                    +sql
                }
            }
        """.trimIndent()

        val findings = rule.lint(code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `unaryPlus - OK pattern5`() {
        val code = """
            val client: KueryClient = KueryClient()
            fun main() {
                client.sql {
                    +"SELECT * FROM user WHERE id = ${'$'}{hoge(id)}"
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
                    +"SELECT * FROM user WHERE id = ${'$'}id"
                }
            }
        """.trimIndent()

        val findings = rule.lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `unaryPlus - NG pattern2`() {
        val code = """
            val client: KueryClient = KueryClient()
            fun main() {
                client.sql {
                    +"SELECT * FROM user WHERE id = ${'$'}{id}"
                }
            }
        """.trimIndent()

        val findings = rule.lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `unaryPlus - NG pattern3`() {
        val code = """
            val client: KueryClient = KueryClient()
            fun main() {
                client.sql {
                    +"SELECT * FROM user WHERE id = ${'$'}{if (hoge) bind(id1) else bind(id2)}"
                }
            }
        """.trimIndent()

        val findings = rule.lint(code)
        assertThat(findings).hasSize(1)
    }
}
