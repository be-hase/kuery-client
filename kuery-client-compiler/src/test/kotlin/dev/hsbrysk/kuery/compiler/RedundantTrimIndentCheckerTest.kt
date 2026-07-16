package dev.hsbrysk.kuery.compiler

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.PluginOption
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class RedundantTrimIndentCheckerTest {
    @Test
    fun `warn on an explicit trimIndent on a template passed to add`() {
        val result = compileWithAutoTrim(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int) {
                Sql { add("SELECT * FROM users WHERE id = ${'$'}id".trimIndent()) }
            }
            """.trimIndent(),
        )
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    @Test
    fun `warn on an explicit trimIndent on a literal passed to unaryPlus`() {
        val result = compileWithAutoTrim(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query() {
                Sql { +"SELECT 1".trimIndent() }
            }
            """.trimIndent(),
        )
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    @Test
    fun `warn on an explicit trimIndent on a const val`() {
        val result = compileWithAutoTrim(
            """
            import dev.hsbrysk.kuery.core.Sql

            const val SELECT_ALL = "SELECT * FROM users"

            fun query() {
                Sql { add(SELECT_ALL.trimIndent()) }
            }
            """.trimIndent(),
        )
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    @Test
    fun `no warning for trimMargin`() {
        // autoTrimIndent does not remove margins, so an explicit trimMargin is not redundant.
        val result = compileWithAutoTrim(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int) {
                Sql { add("|SELECT * FROM users WHERE id = ${'$'}id".trimMargin()) }
            }
            """.trimIndent(),
            allWarningsAsErrors = true,
        )
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_NAME)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `no warning without an explicit trimIndent`() {
        val result = compileWithAutoTrim(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int) {
                Sql { +"SELECT * FROM users WHERE id = ${'$'}id" }
            }
            """.trimIndent(),
            allWarningsAsErrors = true,
        )
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_NAME)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `no warning when the option is off`() {
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int) {
                Sql { add("SELECT * FROM users WHERE id = ${'$'}id".trimIndent()) }
            }
            """.trimIndent(),
            allWarningsAsErrors = true,
        )
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_NAME)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `a variable receiver gets the unsafe warning instead`() {
        val result = compileWithAutoTrim(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(s: String) {
                Sql { add(s.trimIndent()) }
            }
            """.trimIndent(),
        )
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains("KUERY_UNSAFE_SQL_STRING")
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_NAME)
    }

    @Test
    fun `warning can be suppressed by diagnostic name`() {
        val result = compileWithAutoTrim(
            """
            import dev.hsbrysk.kuery.core.Sql

            @Suppress("$DIAGNOSTIC_NAME")
            fun query() {
                Sql { +"SELECT 1".trimIndent() }
            }
            """.trimIndent(),
            allWarningsAsErrors = true,
        )
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_NAME)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    private fun compileWithAutoTrim(
        source: String,
        allWarningsAsErrors: Boolean = false,
    ): JvmCompilationResult = compile(
        source = source,
        allWarningsAsErrors = allWarningsAsErrors,
        pluginOptions = listOf(
            PluginOption(
                KueryClientCompilerCommandLineProcessor.PLUGIN_ID,
                KueryClientCompilerCommandLineProcessor.AUTO_TRIM_INDENT_OPTION_NAME,
                "true",
            ),
        ),
    )

    companion object {
        private const val DIAGNOSTIC_NAME = "KUERY_REDUNDANT_TRIM_INDENT"
    }
}
