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
class UnsafeSqlStringCheckerTest {
    @Test
    fun `warn when a variable is passed to add`() {
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int) {
                val sql = "SELECT * FROM users WHERE id = ${'$'}id"
                Sql { add(sql) }
            }
            """.trimIndent(),
        )
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    @Test
    fun `warn when a variable is passed to unaryPlus`() {
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int) {
                val sql = "SELECT * FROM users WHERE id = ${'$'}id"
                Sql { +sql }
            }
            """.trimIndent(),
        )
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    @Test
    fun `warn when a run block is passed to add`() {
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int) {
                Sql { add(run { "SELECT * FROM users WHERE id = ${'$'}id" }) }
            }
            """.trimIndent(),
        )
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    @Test
    fun `warn when an if expression is passed to add`() {
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int) {
                Sql { add(if (id > 0) "SELECT 1" else "SELECT 2") }
            }
            """.trimIndent(),
        )
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    @Test
    fun `warn when a non-whitelisted method chain is passed to add`() {
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query() {
                Sql { add("SELECT 1".let { it }) }
            }
            """.trimIndent(),
        )
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    @Test
    fun `warn when string concatenation via plus is passed to add`() {
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int) {
                Sql { add("SELECT * FROM users WHERE id = " + id) }
            }
            """.trimIndent(),
        )
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    @Test
    fun `no warning for safe argument forms`() {
        // allWarningsAsErrors = true, so any unexpected warning fails the compilation as well
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
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_NAME)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `warning can be suppressed by diagnostic name`() {
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
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_NAME)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `warning becomes an error with -Werror`() {
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
        private const val DIAGNOSTIC_NAME = "KUERY_UNSAFE_SQL_STRING"
    }
}
