package dev.hsbrysk.kuery.compiler

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Proof that the compile-time trim folding actually fires: when a trimIndent/trimMargin call is
 * folded, the generated classes no longer reference the stdlib trim function, so its name
 * disappears from the constant pool. The fallback tests double as negative controls showing that
 * this detection method is valid. Behavioral equivalence is covered by the functional-test
 * module's TrimFoldingTest.
 */
@OptIn(ExperimentalCompilerApi::class)
class TrimFoldingBytecodeTest {
    @Test
    fun `trimIndent on an interpolated template is folded away`() {
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int) = Sql {
                add(
                    ${TRIPLE_QUOTE}
                    SELECT * FROM users
                    WHERE id = ${'$'}id
                    ${TRIPLE_QUOTE}.trimIndent(),
                )
            }
            """.trimIndent(),
        )
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.classesReference("trimIndent")).isFalse()
    }

    @Test
    fun `trimIndent via unaryPlus is folded away`() {
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int) = Sql {
                +${TRIPLE_QUOTE}
                SELECT * FROM users
                WHERE id = ${'$'}id
                ${TRIPLE_QUOTE}.trimIndent()
            }
            """.trimIndent(),
        )
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.classesReference("trimIndent")).isFalse()
    }

    @Test
    fun `trimIndent on a constant string is folded away`() {
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query() = Sql {
                add(
                    ${TRIPLE_QUOTE}
                    SELECT * FROM users
                    ${TRIPLE_QUOTE}.trimIndent(),
                )
            }
            """.trimIndent(),
        )
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.classesReference("trimIndent")).isFalse()
    }

    @Test
    fun `trimMargin with a literal prefix is folded away`() {
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int) = Sql {
                add(
                    ${TRIPLE_QUOTE}
                    |SELECT * FROM users
                    |WHERE id = ${'$'}id
                    ${TRIPLE_QUOTE}.trimMargin(),
                )
            }
            """.trimIndent(),
        )
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.classesReference("trimMargin")).isFalse()
    }

    @Test
    fun `trimMargin with a colon prefix falls back and stays in bytecode`() {
        // Negative control: the fallback must keep the runtime call, which also proves that
        // the constant-pool scan used by these tests can detect the reference at all.
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int) = Sql {
                add(
                    ${TRIPLE_QUOTE}
                    :x = ${'$'}id
                    ${TRIPLE_QUOTE}.trimMargin(":"),
                )
            }
            """.trimIndent(),
        )
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.classesReference("trimMargin")).isTrue()
    }

    @Test
    fun `trimIndent on a variable falls back and stays in bytecode`() {
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            @Suppress("KUERY_UNSAFE_SQL_STRING")
            fun query(s: String) = Sql {
                add(s.trimIndent())
            }
            """.trimIndent(),
        )
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.classesReference("trimIndent")).isTrue()
    }

    private fun compile(source: String): JvmCompilationResult = KotlinCompilation().apply {
        sources = listOf(SourceFile.kotlin("Sample.kt", source))
        commandLineProcessors = listOf(KueryClientCompilerCommandLineProcessor())
        compilerPluginRegistrars = listOf(KueryClientCompilerPluginRegistrar())
        inheritClassPath = true
        verbose = false
    }.compile()

    // The constant pool stores referenced method names as plain modified-UTF-8 entries, so a
    // simple byte scan is enough to tell whether any generated class still calls the function.
    private fun JvmCompilationResult.classesReference(name: String): Boolean = outputDirectory
        .walkTopDown()
        .filter { it.isFile && it.extension == "class" }
        .also { check(it.any()) { "no classes were generated" } }
        .any { String(it.readBytes(), Charsets.ISO_8859_1).contains(name) }

    companion object {
        private const val TRIPLE_QUOTE = "\"\"\""
    }
}
