package dev.hsbrysk.kuery.compiler

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * The `strict` plugin option registers the SQL-safety diagnostics with error severity instead of
 * warning severity. The diagnostic names are unchanged, so `@Suppress` behaves identically at
 * both severities.
 */
@OptIn(ExperimentalCompilerApi::class)
class StrictModeTest {
    @Test
    fun `an unsafe SQL string is a compile error under strict mode`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun unsafe(fragment: String) = Sql { add(fragment) }
            """.trimIndent(),
            pluginOptions = listOf(strictOption()),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("e: ")
        assertThat(result.messages).contains(UNSAFE_MESSAGE)
    }

    @Test
    fun `an unsafe SQL string under strict mode can be suppressed by diagnostic name`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            @Suppress("KUERY_UNSAFE_SQL_STRING")
            fun unsafe(fragment: String) = Sql { add(fragment) }
            """.trimIndent(),
            pluginOptions = listOf(strictOption()),
        )

        // then
        assertThat(result.messages).doesNotContain(UNSAFE_MESSAGE)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `a SQL syntax failure is a compile error under strict mode`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun broken() = Sql { +"SELECT * FORM users" }
            """.trimIndent(),
            pluginOptions = listOf(strictOption(), sqlSyntaxCheckOption("generic")),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains(SYNTAX_MESSAGE)
    }

    @Test
    fun `a dialect violation is a compile error under strict mode`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun upsert(id: Int, name: String) = Sql {
                +"INSERT INTO users (id, name) VALUES (${'$'}id, ${'$'}name) ON DUPLICATE KEY UPDATE name = ${'$'}name"
            }
            """.trimIndent(),
            pluginOptions = listOf(strictOption(), sqlSyntaxCheckOption("postgresql")),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains(DIALECT_MESSAGE)
    }

    @Test
    fun `strict mode leaves the redundant trimIndent diagnostic a warning`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int) = Sql { +"SELECT * FROM users WHERE id = ${'$'}id".trimIndent() }
            """.trimIndent(),
            pluginOptions = listOf(strictOption(), autoTrimIndentOption()),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains("This trimIndent() is redundant")
    }

    // An explicit -Xwarning-level always wins over strict mode: the configured diagnostic keeps
    // its warning-severity registration, so the user's chosen level applies. (Registering it as
    // an error would instead make kotlinc reject the flag — errors cannot be re-leveled.)
    @Test
    fun `an explicit -Xwarning-level downgrade wins over strict mode`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun unsafe(fragment: String) = Sql { add(fragment) }
            """.trimIndent(),
            pluginOptions = listOf(strictOption()),
            kotlincArguments = listOf("-Xwarning-level=KUERY_UNSAFE_SQL_STRING:warning"),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains("w: ")
        assertThat(result.messages).contains(UNSAFE_MESSAGE)
    }

    @Test
    fun `an explicit -Xwarning-level disabled wins over strict mode`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun unsafe(fragment: String) = Sql { add(fragment) }
            """.trimIndent(),
            pluginOptions = listOf(strictOption()),
            kotlincArguments = listOf("-Xwarning-level=KUERY_UNSAFE_SQL_STRING:disabled"),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).doesNotContain(UNSAFE_MESSAGE)
    }

    // The downgraded diagnostic cannot be asserted on here: the CLI omits warnings from a
    // failing compilation entirely (general K2 behavior, unrelated to strict mode). The
    // downgrade itself is proven by the test above; this one proves the other escalations are
    // untouched by it.
    @Test
    fun `a diagnostic downgraded by -Xwarning-level does not affect the other strict-mode errors`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun unsafe(fragment: String) = Sql { add(fragment) }

            fun broken() = Sql { +"SELECT * FORM users" }
            """.trimIndent(),
            pluginOptions = listOf(strictOption(), sqlSyntaxCheckOption("generic")),
            kotlincArguments = listOf("-Xwarning-level=KUERY_UNSAFE_SQL_STRING:warning"),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains(SYNTAX_MESSAGE)
    }

    companion object {
        // The CLI renders message text without diagnostic names, so assertions match on
        // distinctive fragments of the rendered messages.
        private const val UNSAFE_MESSAGE = "This expression is not a string literal/template"
        private const val SYNTAX_MESSAGE = "The SQL assembled from this block failed to parse"
        private const val DIALECT_MESSAGE = "uses a feature the configured dialect does not support"
    }
}
