package dev.hsbrysk.kuery.compiler

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class SqlSyntaxCheckerDialectTest {
    @Test
    fun `warn when the SQL uses a feature the configured dialect does not support`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int, name: String) {
                Sql { +"INSERT INTO users (id, name) VALUES (${'$'}id, ${'$'}name) ON DUPLICATE KEY UPDATE name = ${'$'}name" }
            }
            """.trimIndent(),
            pluginOptions = listOf(sqlSyntaxCheckOption("postgresql")),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIALECT_DIAGNOSTIC_NAME)
        // The label uses the option vocabulary.
        assertThat(result.messages).contains("(dialect: postgresql)")
    }

    @Test
    fun `no warning when the SQL matches the configured dialect`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int, name: String) {
                Sql { +"SELECT `order` FROM `users` WHERE id = ${'$'}id FOR UPDATE" }
                Sql { +"INSERT INTO users (id, name) VALUES (${'$'}id, ${'$'}name) ON DUPLICATE KEY UPDATE name = ${'$'}name" }
                Sql { +"SELECT * FROM users WHERE name LIKE ${'$'}name LIMIT ${'$'}id OFFSET ${'$'}id" }
            }
            """.trimIndent(),
            allWarningsAsErrors = true,
            pluginOptions = listOf(sqlSyntaxCheckOption("mysql")),
        )

        // then
        assertThat(result.messages).doesNotContain(DIALECT_DIAGNOSTIC_NAME)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `the ansi mode flags a vendor-specific feature unlike generic`() {
        // ansi is a real, strict feature set — the documented distinction from generic.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int) {
                Sql { +"INSERT INTO users (id) VALUES (${'$'}id) ON DUPLICATE KEY UPDATE id = ${'$'}id" }
            }
            """.trimIndent(),
            pluginOptions = listOf(sqlSyntaxCheckOption("ansi")),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIALECT_DIAGNOSTIC_NAME)
        // The label uses the option vocabulary, not the parser's enum name ("ansi_sql").
        assertThat(result.messages).contains("(dialect: ansi)")
    }

    @Test
    fun `the H2 MERGE KEY upsert draws a syntax warning as a known parser limitation`() {
        // JSqlParser has no grammar for H2's flagship upsert, so even the h2 mode flags it — a
        // deliberate trade over a shape-based skip list, which would also swallow real typos in
        // any statement matching the shape. The documented escape is @Suppress.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int) {
                Sql { +"MERGE INTO users (id) KEY(id) VALUES (${'$'}id)" }
            }
            """.trimIndent(),
            pluginOptions = listOf(sqlSyntaxCheckOption("h2")),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    @Test
    fun `no dialect warning under the generic check even for a vendor-specific feature`() {
        // generic parses without any feature validation, so a MySQL-only feature draws neither
        // warning.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int, name: String) {
                Sql { +"INSERT INTO users (id, name) VALUES (${'$'}id, ${'$'}name) ON DUPLICATE KEY UPDATE name = ${'$'}name" }
            }
            """.trimIndent(),
            allWarningsAsErrors = true,
            pluginOptions = listOf(sqlSyntaxCheckOption("generic")),
        )

        // then
        assertThat(result.messages).doesNotContain(DIALECT_DIAGNOSTIC_NAME)
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_NAME)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `a syntax error rather than a dialect violation is reported when the SQL does not parse`() {
        // A statement that fails to parse cannot be feature-checked, so only the syntax warning
        // fires even with a dialect configured.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query() {
                Sql { +"SELCT * FROM users" }
            }
            """.trimIndent(),
            pluginOptions = listOf(sqlSyntaxCheckOption("postgresql")),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
        assertThat(result.messages).doesNotContain(DIALECT_DIAGNOSTIC_NAME)
    }

    @Test
    fun `dialect warning can be suppressed by diagnostic name`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            @Suppress("$DIALECT_DIAGNOSTIC_NAME")
            fun query(id: Int, name: String) {
                Sql { +"INSERT INTO users (id, name) VALUES (${'$'}id, ${'$'}name) ON DUPLICATE KEY UPDATE name = ${'$'}name" }
            }
            """.trimIndent(),
            allWarningsAsErrors = true,
            pluginOptions = listOf(sqlSyntaxCheckOption("postgresql")),
        )

        // then
        assertThat(result.messages).doesNotContain(DIALECT_DIAGNOSTIC_NAME)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    companion object {
        private const val DIAGNOSTIC_NAME = "KUERY_SQL_SYNTAX"
        private const val DIALECT_DIAGNOSTIC_NAME = "KUERY_SQL_DIALECT"
    }
}
