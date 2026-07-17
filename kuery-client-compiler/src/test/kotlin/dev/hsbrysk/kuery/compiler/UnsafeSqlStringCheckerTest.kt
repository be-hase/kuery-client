package dev.hsbrysk.kuery.compiler

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class UnsafeSqlStringCheckerTest {
    @Test
    fun `warn when a variable is passed to add`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int) {
                val sql = "SELECT * FROM users WHERE id = ${'$'}id"
                Sql { add(sql) }
            }
            """.trimIndent(),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    @Test
    fun `warn when a variable is passed to unaryPlus`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int) {
                val sql = "SELECT * FROM users WHERE id = ${'$'}id"
                Sql { +sql }
            }
            """.trimIndent(),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    @Test
    fun `warn when a run block is passed to add`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int) {
                Sql { add(run { "SELECT * FROM users WHERE id = ${'$'}id" }) }
            }
            """.trimIndent(),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    @Test
    fun `no warning when an if expression with safe branches is passed to add`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int) {
                Sql { add(if (id > 0) "SELECT 1" else "SELECT 2") }
                Sql { add(if (id > 0) "SELECT * FROM users WHERE id = ${'$'}id" else "SELECT 2") }
                Sql { +(if (id > 0) "id = ${'$'}id" else "TRUE") }
            }
            """.trimIndent(),
            allWarningsAsErrors = true,
        )

        // then
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_NAME)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `no warning when a when expression with safe branches is passed to add`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            const val DEFAULT_ORDER = "ORDER BY id"

            fun query(sort: String?, id: Int) {
                Sql {
                    add(
                        when (sort) {
                            "name" -> "ORDER BY name"
                            "created" -> "ORDER BY created_at ${'$'}id".trimIndent()
                            else -> DEFAULT_ORDER
                        },
                    )
                }
            }
            """.trimIndent(),
            allWarningsAsErrors = true,
        )

        // then
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_NAME)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `no warning when a when expression has an error branch`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(sort: String, id: Int) {
                Sql {
                    add(
                        when (sort) {
                            "name" -> "ORDER BY name"
                            "created" -> "ORDER BY created_at, id = ${'$'}id"
                            else -> error("unsupported sort: ${'$'}sort")
                        },
                    )
                }
            }
            """.trimIndent(),
            allWarningsAsErrors = true,
        )

        // then
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_NAME)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `no warning when an if expression has a throw branch`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int) {
                Sql {
                    +(
                        if (id > 0) {
                            "SELECT * FROM users WHERE id = ${'$'}id"
                        } else {
                            throw IllegalArgumentException("id must be positive")
                        }
                    )
                }
            }
            """.trimIndent(),
            allWarningsAsErrors = true,
        )

        // then
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_NAME)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `warn when an if expression has an unsafe branch`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int, fragment: String) {
                Sql { add(if (id > 0) "SELECT 1" else fragment) }
            }
            """.trimIndent(),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    @Test
    fun `warn when a non-whitelisted method chain is passed to add`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query() {
                Sql { add("SELECT 1".let { it }) }
            }
            """.trimIndent(),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    @Test
    fun `warn when string concatenation via plus is passed to add`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int) {
                Sql { add("SELECT * FROM users WHERE id = " + id) }
            }
            """.trimIndent(),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    @Test
    fun `no warning for safe argument forms`() {
        // allWarningsAsErrors = true, so any unexpected warning fails the compilation as well
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.DelicateKueryClientApi
            import dev.hsbrysk.kuery.core.Sql

            const val SELECT_ALL = "SELECT * FROM users"

            @OptIn(DelicateKueryClientApi::class)
            fun query(id: Int) {
                Sql { add("SELECT 1") }
                Sql { +"SELECT 1" }
                Sql { add("SELECT * FROM users WHERE id = ${'$'}id") }
                Sql { +"SELECT * FROM users WHERE id = ${'$'}id" }
                Sql { add("SELECT * FROM users WHERE id = ${'$'}id".trimIndent()) }
                Sql { add("|SELECT * FROM users WHERE id = ${'$'}id".trimMargin("|")) }
                Sql { add(SELECT_ALL) }
                Sql {
                    val sql = "SELECT * FROM users WHERE id = ${'$'}{bind(id)}"
                    addUnsafe(sql)
                }
            }
            """.trimIndent(),
            allWarningsAsErrors = true,
        )

        // then
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_NAME)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `no warning for extension function helpers writing common query parts`() {
        // Mirrors the documented helper style: common WHERE clauses etc. written as
        // SqlBuilder extension functions using add/unaryPlus with string templates.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql
            import dev.hsbrysk.kuery.core.SqlBuilder

            fun SqlBuilder.whereActiveUser(tenantId: Int, name: String?) {
                +"WHERE tenant_id = ${'$'}tenantId"
                +"AND status = 'ACTIVE'"
                name?.let { +"AND name = ${'$'}it" }
            }

            fun SqlBuilder.paging(limit: Int, offset: Int) {
                add("LIMIT ${'$'}limit OFFSET ${'$'}offset")
            }

            fun query(tenantId: Int) {
                Sql {
                    +"SELECT * FROM users"
                    whereActiveUser(tenantId, null)
                    paging(10, 0)
                }
            }
            """.trimIndent(),
            allWarningsAsErrors = true,
        )

        // then
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_NAME)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `warn when an extension helper passes its parameter to unaryPlus`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.SqlBuilder

            fun SqlBuilder.rawFragment(fragment: String) {
                +fragment
            }
            """.trimIndent(),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    @Test
    fun `warning can be suppressed by diagnostic name`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            @Suppress("$DIAGNOSTIC_NAME")
            fun query(id: Int) {
                val sql = "SELECT * FROM users WHERE id = ${'$'}id"
                Sql { add(sql) }
            }
            """.trimIndent(),
            allWarningsAsErrors = true,
        )

        // then
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_NAME)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `warning becomes an error with -Werror`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int) {
                val sql = "SELECT * FROM users WHERE id = ${'$'}id"
                Sql { add(sql) }
            }
            """.trimIndent(),
            allWarningsAsErrors = true,
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    companion object {
        private const val DIAGNOSTIC_NAME = "KUERY_UNSAFE_SQL_STRING"
    }
}
