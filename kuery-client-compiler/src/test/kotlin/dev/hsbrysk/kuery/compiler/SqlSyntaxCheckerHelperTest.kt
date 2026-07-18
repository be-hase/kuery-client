package dev.hsbrysk.kuery.compiler

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class SqlSyntaxCheckerHelperTest {
    @Test
    fun `warn when a static helper contributes broken SQL`() {
        // A same-module SqlBuilder extension helper whose body is only static adds is inlined
        // into the reconstruction.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql
            import dev.hsbrysk.kuery.core.SqlBuilder

            fun SqlBuilder.brokenWhere(id: Int) {
                +"WHRE id = ${'$'}id"
            }

            fun query(id: Int) {
                Sql {
                    +"SELECT * FROM users"
                    brokenWhere(id)
                }
            }
            """.trimIndent(),
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
        // The warning is anchored to the helper call site (line 11 of the compiled source).
        assertThat(result.messages).contains("Sample.kt:11")
    }

    @Test
    fun `no warning when static helpers assemble a valid statement`() {
        // Bind numbering continues through the helper (:p0 for id, :p1/:p2 inside paging).
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql
            import dev.hsbrysk.kuery.core.SqlBuilder

            fun SqlBuilder.paging(limit: Int, offset: Int) {
                add("LIMIT ${'$'}limit OFFSET ${'$'}offset")
            }

            fun query(id: Int) {
                Sql {
                    +"SELECT * FROM users WHERE id = ${'$'}id"
                    paging(10, 0)
                }
            }
            """.trimIndent(),
            allWarningsAsErrors = true,
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_NAME)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `warn when a nested static helper contributes broken SQL`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql
            import dev.hsbrysk.kuery.core.SqlBuilder

            fun SqlBuilder.limitOnly(limit: Int) {
                +"LIMIT ${'$'}limit OFFST 5"
            }

            fun SqlBuilder.paging(limit: Int) {
                limitOnly(limit)
            }

            fun query() {
                Sql {
                    +"SELECT * FROM users"
                    paging(10)
                }
            }
            """.trimIndent(),
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    @Test
    fun `warn when a member helper of the enclosing class contributes broken SQL`() {
        // Repositories commonly declare private member extension helpers; the enclosing-class
        // dispatch receiver does not prevent inlining.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql
            import dev.hsbrysk.kuery.core.SqlBuilder

            class UserRepository {
                fun query(id: Int) = Sql {
                    +"SELECT * FROM users"
                    condition(id)
                }

                private fun SqlBuilder.condition(id: Int) {
                    +"WHRE id = ${'$'}id"
                }
            }
            """.trimIndent(),
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    @Test
    fun `no warning when a helper argument mutates the builder`() {
        // The argument expression runs against the builder before the helper body, so the
        // block's SQL is not the helper body alone — it must be skipped.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql
            import dev.hsbrysk.kuery.core.SqlBuilder

            fun SqlBuilder.limitOnly(limit: Int) {
                +"LIMIT ${'$'}limit"
            }

            fun query() {
                Sql {
                    limitOnly(
                        run {
                            +"SELECT * FROM users"
                            10
                        },
                    )
                }
            }
            """.trimIndent(),
            allWarningsAsErrors = true,
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_NAME)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `no warning when a helper is dynamic`() {
        // The helper's body contains control flow, so the complete SQL is not statically known
        // and the block is skipped even though another fragment is broken.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql
            import dev.hsbrysk.kuery.core.SqlBuilder

            fun SqlBuilder.filter(name: String?) {
                name?.let { +"AND name = ${'$'}it" }
            }

            fun query(name: String?) {
                Sql {
                    +"SELCT * FROM users"
                    filter(name)
                }
            }
            """.trimIndent(),
            allWarningsAsErrors = true,
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_NAME)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `no warning when a helper returns early with an explicit return`() {
        // An explicit return is control flow the inlining does not follow, so the helper is not
        // inlinable and the block is skipped.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql
            import dev.hsbrysk.kuery.core.SqlBuilder

            fun SqlBuilder.brokenWhere(id: Int) {
                return add("WHRE id = ${'$'}id")
            }

            fun query(id: Int) {
                Sql {
                    +"SELECT * FROM users"
                    brokenWhere(id)
                }
            }
            """.trimIndent(),
            allWarningsAsErrors = true,
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_NAME)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `no warning when a helper comes from another module`() {
        // The body of a compiled helper (here core's values()) is not available to the checker,
        // so the block is skipped.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql
            import dev.hsbrysk.kuery.core.values

            fun query() {
                Sql {
                    +"INSERT INTO users (id) SELCT"
                    values(listOf(listOf(1)))
                }
            }
            """.trimIndent(),
            allWarningsAsErrors = true,
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_NAME)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `no warning for a self-recursive helper`() {
        // Recursion cannot be reconstructed; the checker must terminate and skip the block.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql
            import dev.hsbrysk.kuery.core.SqlBuilder

            fun SqlBuilder.recurse(n: Int) {
                +"AND x = ${'$'}n"
                recurse(n)
            }

            fun query() {
                Sql {
                    +"SELCT * FROM users"
                    recurse(1)
                }
            }
            """.trimIndent(),
            allWarningsAsErrors = true,
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_NAME)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    companion object {
        private const val DIAGNOSTIC_NAME = "KUERY_SQL_SYNTAX"
    }
}
