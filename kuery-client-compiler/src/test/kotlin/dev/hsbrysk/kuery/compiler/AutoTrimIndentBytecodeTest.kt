package dev.hsbrysk.kuery.compiler

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Proof that autoTrimIndent trims templates and constants at *compile time*: when folded, the
 * generated classes contain no constant-pool reference to the stdlib trimIndent function. The
 * variable-argument case shows the reference for the runtime wrap (also validating the scan
 * itself), and the option-off cases show that nothing is injected by default. Behavioral
 * equivalence is covered by the functional-test-auto-trim module.
 */
@OptIn(ExperimentalCompilerApi::class)
class AutoTrimIndentBytecodeTest {
    @Test
    fun `template argument is trimmed at compile time`() {
        val result = compile(autoTrimIndent = true, source = TEMPLATE_ARGUMENT_SOURCE)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.classesReference("trimIndent")).isFalse()
    }

    @Test
    fun `constant argument is trimmed at compile time`() {
        val result = compile(
            autoTrimIndent = true,
            source = """
            import dev.hsbrysk.kuery.core.Sql

            fun query() = Sql {
                +${TRIPLE_QUOTE}
                SELECT * FROM users
                ${TRIPLE_QUOTE}
            }
            """.trimIndent(),
        )
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.classesReference("trimIndent")).isFalse()
    }

    @Test
    fun `variable argument is trimmed at runtime`() {
        val result = compile(autoTrimIndent = true, source = VARIABLE_ARGUMENT_SOURCE)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.classesReference("trimIndent")).isTrue()
    }

    @Test
    fun `nothing is injected for a template argument when the option is off`() {
        val result = compile(autoTrimIndent = false, source = TEMPLATE_ARGUMENT_SOURCE)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.classesReference("trimIndent")).isFalse()
    }

    @Test
    fun `nothing is injected for a variable argument when the option is off`() {
        val result = compile(autoTrimIndent = false, source = VARIABLE_ARGUMENT_SOURCE)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.classesReference("trimIndent")).isFalse()
    }

    private fun compile(
        autoTrimIndent: Boolean,
        source: String,
    ): JvmCompilationResult = compile(
        source = source,
        pluginOptions = listOf(autoTrimIndentOption(autoTrimIndent.toString())),
    )

    // The constant pool stores referenced method names as plain modified-UTF-8 entries, so a
    // simple byte scan is enough to tell whether any generated class calls the function.
    private fun JvmCompilationResult.classesReference(name: String): Boolean = outputDirectory
        .walkTopDown()
        .filter { it.isFile && it.extension == "class" }
        .also { check(it.any()) { "no classes were generated" } }
        .any { String(it.readBytes(), Charsets.ISO_8859_1).contains(name) }

    companion object {
        private const val TRIPLE_QUOTE = "\"\"\""

        private val TEMPLATE_ARGUMENT_SOURCE = """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int) = Sql {
                +${TRIPLE_QUOTE}
                SELECT * FROM users
                WHERE id = ${'$'}id
                ${TRIPLE_QUOTE}
            }
        """.trimIndent()

        private val VARIABLE_ARGUMENT_SOURCE = """
            import dev.hsbrysk.kuery.core.Sql

            @Suppress("KUERY_UNSAFE_SQL_STRING")
            fun query(s: String) = Sql {
                add(s)
            }
        """.trimIndent()
    }
}
