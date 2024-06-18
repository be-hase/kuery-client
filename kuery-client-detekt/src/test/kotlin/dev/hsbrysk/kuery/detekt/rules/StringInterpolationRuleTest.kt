package dev.hsbrysk.kuery.detekt.rules

import assertk.assertThat
import assertk.assertions.hasSize
import io.gitlab.arturbosch.detekt.rules.KotlinCoreEnvironmentTest
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.compileAndLintWithContext
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.junit.jupiter.api.Test

@KotlinCoreEnvironmentTest
class StringInterpolationRuleTest(private val env: KotlinCoreEnvironment) {
    private val rule = StringInterpolationRule(
        TestConfig("allowRegexes" to listOf("^hoge(.*)$")),
    )

    @Test
    fun `add - OK pattern1`() {
        val code = """
            import dev.hsbrysk.kuery.core.KueryClient

            class SomeRepository(private val client: KueryClient) {
                suspend fun someFun(id: Int) {
                    client.sql {
                        add("SELECT * FROM user WHERE id = ${'$'}{bind(id)}")
                    }
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `add - OK pattern2`() {
        val code = """
            import dev.hsbrysk.kuery.core.KueryClient

            class SomeRepository(private val client: KueryClient) {
                suspend fun someFun(id: Int) {
                    client.sql {
                        add("SELECT * FROM user WHERE id = ${'$'}{  bind(id)  }")
                    }
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `add - OK pattern3`() {
        val code = """
            import dev.hsbrysk.kuery.core.KueryClient

            class SomeRepository(private val client: KueryClient) {
                suspend fun someFun(id: Int, status: String) {
                    client.sql {
                        add("SELECT * FROM user WHERE id = ${'$'}{bind(id)} AND status = ${'$'}{bind(status)}")
                    }
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `add - OK pattern4`() {
        val code = """
            import dev.hsbrysk.kuery.core.KueryClient

            class SomeRepository(private val client: KueryClient) {
                suspend fun someFun(id: Int) {
                    client.sql {
                        val sql = "SELECT * FROM user WHERE id = ${'$'}id"
                        add(sql)
                    }
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `add - OK pattern5`() {
        val code = """
            import dev.hsbrysk.kuery.core.KueryClient

            class SomeRepository(private val client: KueryClient) {
                suspend fun someFun(id: Int) {
                    client.sql {
                        add("SELECT * FROM user WHERE id = ${'$'}{hoge(id)}")
                    }
                }

                private fun hoge(id: Int): String {
                    return "hoge${'$'}id"
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `add - OK pattern6`() {
        val code = """
            import dev.hsbrysk.kuery.core.KueryClient
            import dev.hsbrysk.kuery.core.SqlBuilder

            class SomeRepository(private val client: KueryClient) {
                suspend fun someFun(id: Int) {
                    client.sql {
                        add("SELECT * FROM user WHERE")
                        idEqualsTo(id)
                    }
                }

                private fun SqlBuilder.idEqualsTo(id: Int) {
                    add("id = ${'$'}{bind(id)}")
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `add - NG pattern1`() {
        val code = """
            import dev.hsbrysk.kuery.core.KueryClient

            class SomeRepository(private val client: KueryClient) {
                suspend fun someFun(id: Int) {
                    client.sql {
                        add("SELECT * FROM user WHERE id = ${'$'}id")
                    }
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `add - NG pattern2`() {
        val code = """
            import dev.hsbrysk.kuery.core.KueryClient

            class SomeRepository(private val client: KueryClient) {
                suspend fun someFun(id: Int) {
                    client.sql {
                        add("SELECT * FROM user WHERE id = ${'$'}{id}")
                    }
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `add - NG pattern3`() {
        val code = """
            import dev.hsbrysk.kuery.core.KueryClient

            class SomeRepository(private val client: KueryClient) {
                suspend fun someFun(id1: Int, id2: Int) {
                    client.sql {
                        add("SELECT * FROM user WHERE id = ${'$'}{if (hoge) bind(id1) else bind(id2)}")
                    }
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `add - NG pattern4`() {
        val code = """
            import dev.hsbrysk.kuery.core.KueryClient
            import dev.hsbrysk.kuery.core.SqlBuilder

            class SomeRepository(private val client: KueryClient) {
                suspend fun someFun(id: Int) {
                    client.sql {
                        add("SELECT * FROM user WHERE")
                        idEqualsTo(id)
                    }
                }

                private fun SqlBuilder.idEqualsTo(id: Int) {
                    add("id = ${'$'}id")
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `add - NG pattern5`() {
        val code = """
            import dev.hsbrysk.kuery.core.KueryClient

            class SomeRepository(private val client: KueryClient) {
                suspend fun someFun(id: Int) {
                    client.sql {
                        add("SELECT * FROM user WHERE id = ${'$'}id".removePrefix("hoge").removePrefix("bar"))
                    }
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `add - NG pattern6`() {
        val code = """
            import dev.hsbrysk.kuery.core.KueryClient

            class SomeRepository(private val client: KueryClient) {
                suspend fun someFun(id: Int) {
                    client.sql {
                        add(
                        {TRIPLE_QUOTES}
                        SELECT * FROM user WHERE id = ${'$'}id
                        {TRIPLE_QUOTES}.trimIndent()
                        )
                    }
                }
            }
        """.trimIndent().replace("{TRIPLE_QUOTES}", "\"\"\"")

        val findings = rule.compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `unaryPlus - OK pattern1`() {
        val code = """
            import dev.hsbrysk.kuery.core.KueryClient

            class SomeRepository(private val client: KueryClient) {
                suspend fun someFun(id: Int) {
                    client.sql {
                        +"SELECT * FROM user WHERE id = ${'$'}{bind(id)}"
                    }
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `unaryPlus - OK pattern2`() {
        val code = """
            import dev.hsbrysk.kuery.core.KueryClient

            class SomeRepository(private val client: KueryClient) {
                suspend fun someFun(id: Int) {
                    client.sql {
                        +"SELECT * FROM user WHERE id = ${'$'}{ bind(id) }"
                    }
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `unaryPlus - OK pattern3`() {
        val code = """
            import dev.hsbrysk.kuery.core.KueryClient

            class SomeRepository(private val client: KueryClient) {
                suspend fun someFun(id: Int, status: String) {
                    client.sql {
                        +"SELECT * FROM user WHERE id = ${'$'}{bind(id)} AND status = ${'$'}{bind(status)}"
                    }
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `unaryPlus - OK pattern4`() {
        // OKだが、別のruleに引っかかります
        val code = """
            import dev.hsbrysk.kuery.core.KueryClient

            class SomeRepository(private val client: KueryClient) {
                suspend fun someFun(id: Int) {
                    client.sql {
                        val sql = "SELECT * FROM user WHERE id = ${'$'}id"
                        +sql
                    }
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `unaryPlus - OK pattern5`() {
        val code = """
            import dev.hsbrysk.kuery.core.KueryClient

            class SomeRepository(private val client: KueryClient) {
                suspend fun someFun(id: Int) {
                    client.sql {
                       +"SELECT * FROM user WHERE id = ${'$'}{hoge(id)}"
                    }
                }

                private fun hoge(id: Int): String {
                    return "hoge${'$'}id"
                }
}
        """.trimIndent()

        val findings = rule.compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `unaryPlus - OK pattern6`() {
        val code = """
            import dev.hsbrysk.kuery.core.KueryClient
            import dev.hsbrysk.kuery.core.SqlBuilder

            class SomeRepository(private val client: KueryClient) {
                suspend fun someFun(id: Int) {
                    client.sql {
                        +"SELECT * FROM user WHERE"
                        idEqualsTo(id)
                    }
                }

                private fun SqlBuilder.idEqualsTo(id: Int) {
                    +"id = ${'$'}{bind(id)}"
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `unaryPlus - NG pattern1`() {
        val code = """
            import dev.hsbrysk.kuery.core.KueryClient

            class SomeRepository(private val client: KueryClient) {
                suspend fun someFun(id: Int) {
                    client.sql {
                       +"SELECT * FROM user WHERE id = ${'$'}id"
                    }
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `unaryPlus - NG pattern2`() {
        val code = """
            import dev.hsbrysk.kuery.core.KueryClient

            class SomeRepository(private val client: KueryClient) {
                suspend fun someFun(id: Int) {
                    client.sql {
                       +"SELECT * FROM user WHERE id = ${'$'}{id}"
                    }
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `unaryPlus - NG pattern3`() {
        val code = """
            import dev.hsbrysk.kuery.core.KueryClient

            class SomeRepository(private val client: KueryClient) {
                suspend fun someFun(id1: Int, id2: Int) {
                    client.sql {
                        +"SELECT * FROM user WHERE id = ${'$'}{if (hoge) bind(id1) else bind(id2)}"
                    }
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `unaryPlus - NG pattern4`() {
        val code = """
            import dev.hsbrysk.kuery.core.KueryClient
            import dev.hsbrysk.kuery.core.SqlBuilder

            class SomeRepository(private val client: KueryClient) {
                suspend fun someFun(id: Int) {
                    client.sql {
                        +"SELECT * FROM user WHERE"
                        idEqualsTo(id)
                    }
                }

                private fun SqlBuilder.idEqualsTo(id: Int) {
                    +"id = ${'$'}id"
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `unaryPlus - NG pattern5`() {
        val code = """
            import dev.hsbrysk.kuery.core.KueryClient

            class SomeRepository(private val client: KueryClient) {
                suspend fun someFun(id: Int) {
                    client.sql {
                        +"SELECT * FROM user WHERE id = ${'$'}id".removePrefix("hoge").removePrefix("bar")
                    }
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `unaryPlus - NG pattern6`() {
        val code = """
            import dev.hsbrysk.kuery.core.KueryClient

            class SomeRepository(private val client: KueryClient) {
                suspend fun someFun(id: Int) {
                    client.sql {
                        +{TRIPLE_QUOTES}
                        SELECT * FROM user WHERE id = ${'$'}id
                        {TRIPLE_QUOTES}.trimIndent()
                    }
                }
            }
        """.trimIndent().replace("{TRIPLE_QUOTES}", "\"\"\"")

        val findings = rule.compileAndLintWithContext(env, code)
        assertThat(findings).hasSize(1)
    }
}
