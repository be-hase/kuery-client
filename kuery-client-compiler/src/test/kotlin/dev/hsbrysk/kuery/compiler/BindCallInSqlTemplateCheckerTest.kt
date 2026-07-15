package dev.hsbrysk.kuery.compiler

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class BindCallInSqlTemplateCheckerTest {
    @Test
    fun `warn when bind is called inside a template passed to unaryPlus`() {
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
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    @Test
    fun `warn when bind is called inside a template passed to add`() {
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
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    @Test
    fun `warn when bind is called indirectly inside an interpolated value`() {
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
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    @Test
    fun `warn when bind is called inside a template in an if branch passed to add`() {
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
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    @Test
    fun `no warning when bind is used with addUnsafe`() {
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
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_NAME)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `no warning for plain interpolation`() {
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
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_NAME)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `warning can be suppressed by diagnostic name`() {
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
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_NAME)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `warning becomes an error with -Werror`() {
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.DelicateKueryClientApi
            import dev.hsbrysk.kuery.core.Sql

            @OptIn(DelicateKueryClientApi::class)
            fun query(id: Int) {
                Sql { +"SELECT * FROM users WHERE id = ${'$'}{bind(id)}" }
            }
            """.trimIndent(),
            allWarningsAsErrors = true,
        )
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    private fun compile(
        source: String,
        allWarningsAsErrors: Boolean = false,
    ): JvmCompilationResult = KotlinCompilation().apply {
        sources = listOf(SourceFile.kotlin("Sample.kt", source))
        commandLineProcessors = listOf(KueryClientCompilerCommandLineProcessor())
        compilerPluginRegistrars = listOf(KueryClientCompilerPluginRegistrar())
        inheritClassPath = true
        verbose = false
        this.allWarningsAsErrors = allWarningsAsErrors
    }.compile()

    companion object {
        private const val DIAGNOSTIC_NAME = "KUERY_BIND_CALL_IN_SQL_TEMPLATE"
    }
}
