package dev.hsbrysk.kuery.compiler

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class BindCallInSqlTemplateCheckerTest {
    @Test
    fun `bind call inside a template passed to unaryPlus is a compile error`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.DelicateKueryClientApi
            import dev.hsbrysk.kuery.core.Sql

            @OptIn(DelicateKueryClientApi::class)
            fun query(id: Int) {
                Sql { +"SELECT * FROM users WHERE id = ${'$'}{bind(id)}" }
            }
            """.trimIndent(),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains(DIAGNOSTIC_MESSAGE)
    }

    @Test
    fun `bind call inside a template passed to add is a compile error`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.DelicateKueryClientApi
            import dev.hsbrysk.kuery.core.Sql

            @OptIn(DelicateKueryClientApi::class)
            fun query(id: Int) {
                Sql { add("SELECT * FROM users WHERE id = ${'$'}{bind(id)}") }
            }
            """.trimIndent(),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains(DIAGNOSTIC_MESSAGE)
    }

    @Test
    fun `indirect bind call inside an interpolated value is a compile error`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.DelicateKueryClientApi
            import dev.hsbrysk.kuery.core.Sql

            @OptIn(DelicateKueryClientApi::class)
            fun query(ids: List<Int>) {
                Sql { +"SELECT * FROM users WHERE id IN (${'$'}{ids.joinToString(",") { bind(it) }})" }
            }
            """.trimIndent(),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains(DIAGNOSTIC_MESSAGE)
    }

    @Test
    fun `bind call inside a template in an if branch passed to add is a compile error`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.DelicateKueryClientApi
            import dev.hsbrysk.kuery.core.Sql

            @OptIn(DelicateKueryClientApi::class)
            fun query(id: Int) {
                Sql { add(if (id > 0) "WHERE id = ${'$'}{bind(id)}" else "WHERE TRUE") }
            }
            """.trimIndent(),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains(DIAGNOSTIC_MESSAGE)
    }

    @Test
    fun `no diagnostic when bind is used with addUnsafe`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.DelicateKueryClientApi
            import dev.hsbrysk.kuery.core.Sql

            @OptIn(DelicateKueryClientApi::class)
            fun query(id: Int, column: String) {
                Sql { addUnsafe("SELECT * FROM users WHERE ${'$'}column = ${'$'}{bind(id)}") }
            }
            """.trimIndent(),
            allWarningsAsErrors = true,
        )

        // then
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_MESSAGE)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `no diagnostic for plain interpolation`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int) {
                Sql { +"SELECT * FROM users WHERE id = ${'$'}id" }
                Sql { add("SELECT * FROM users WHERE id = ${'$'}id") }
            }
            """.trimIndent(),
            allWarningsAsErrors = true,
        )

        // then
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_MESSAGE)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    // Pins K2 behavior: @Suppress by diagnostic name silences even error-severity plugin
    // diagnostics. Not advertised in the error message (there is no valid reason to keep the
    // code), but recorded here so a Kotlin upgrade that changes it is noticed.
    @Test
    fun `the error can still be suppressed by diagnostic name`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.DelicateKueryClientApi
            import dev.hsbrysk.kuery.core.Sql

            @OptIn(DelicateKueryClientApi::class)
            @Suppress("$DIAGNOSTIC_NAME")
            fun query(id: Int) {
                Sql { +"SELECT * FROM users WHERE id = ${'$'}{bind(id)}" }
            }
            """.trimIndent(),
            allWarningsAsErrors = true,
        )

        // then
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_MESSAGE)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    companion object {
        private const val DIAGNOSTIC_NAME = "KUERY_BIND_CALL_IN_SQL_TEMPLATE"

        // The CLI renders the message text without the diagnostic name, so assertions match on
        // a distinctive fragment of the message instead.
        private const val DIAGNOSTIC_MESSAGE = "bind() must not be called inside a string template"
    }
}
