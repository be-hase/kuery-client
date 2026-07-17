package dev.hsbrysk.kuery.compiler

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class SqlSyntaxCheckerTest {
    @Test
    fun `warn when the SQL of a block does not parse`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query() {
                Sql { +"SELCT * FROM users" }
            }
            """.trimIndent(),
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    @Test
    fun `warn when a statement assembled from multiple adds does not parse`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int) {
                Sql {
                    +"SELECT *"
                    +"FORM users"
                    +"WHERE id = ${'$'}id"
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
    fun `no warning for valid statically-known SQL forms`() {
        // allWarningsAsErrors = true, so any unexpected warning fails the compilation as well
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            const val SELECT_ALL = "SELECT * FROM users"

            fun query(id: Int, name: String) {
                Sql { +"SELECT 1" }
                Sql { add("SELECT * FROM users WHERE id = ${'$'}id AND name = ${'$'}name") }
                Sql {
                    +"SELECT *"
                    +"FROM users"
                    +"WHERE id = ${'$'}id"
                }
                Sql { add("SELECT *\nFROM users\nWHERE id = ${'$'}id".trimIndent()) }
                Sql { add(SELECT_ALL) }
                Sql { +"INSERT INTO users (id, name) VALUES (${'$'}id, ${'$'}name)" }
                Sql { +"UPDATE users SET name = ${'$'}name WHERE id = ${'$'}id" }
            }

            // Common MySQL / PostgreSQL flavored syntax must not draw false positives.
            fun dialects(id: Int, name: String) {
                Sql { +"SELECT `order` FROM `users` WHERE id = ${'$'}id" }
                Sql { +"INSERT INTO users (id, name) VALUES (${'$'}id, ${'$'}name) ON DUPLICATE KEY UPDATE name = ${'$'}name" }
                Sql { +"INSERT INTO users (id, name) VALUES (${'$'}id, ${'$'}name) ON CONFLICT (id) DO UPDATE SET name = ${'$'}name" }
                Sql { +"SELECT id::text FROM users" }
                Sql { +"SELECT * FROM users WHERE name LIKE ${'$'}name LIMIT ${'$'}id OFFSET ${'$'}id" }
                Sql { +"SELECT * FROM users WHERE id = ${'$'}id FOR UPDATE" }
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
    fun `no warning when the block contains statements other than add or unaryPlus`() {
        // The full statement is not statically known, so even a broken fragment is skipped.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(name: String?) {
                Sql {
                    +"SELCT * FROM users"
                    name?.let { +"WHERE name = ${'$'}it" }
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
    fun `no warning when the sql string is not statically known`() {
        // The variable already draws KUERY_UNSAFE_SQL_STRING; the syntax check must stay silent.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(sql: String) {
                Sql { add(sql) }
            }
            """.trimIndent(),
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_NAME)
    }

    @Test
    fun `no warning when the option is not enabled`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query() {
                Sql { +"SELCT * FROM users" }
            }
            """.trimIndent(),
            allWarningsAsErrors = true,
        )

        // then
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_NAME)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `warning can be suppressed by diagnostic name`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            @Suppress("$DIAGNOSTIC_NAME")
            fun query() {
                Sql { +"SELCT * FROM users" }
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
    fun `the warning is anchored to the add whose line fails to parse`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int) {
                Sql {
                    +"SELECT *"
                    +"FROM users"
                    +"WHRE id = ${'$'}id"
                }
            }
            """.trimIndent(),
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        // The broken fragment is on line 7 of the compiled source.
        assertThat(result.messages).contains("Sample.kt:7")
    }

    companion object {
        private const val DIAGNOSTIC_NAME = "KUERY_SQL_SYNTAX"
    }
}
