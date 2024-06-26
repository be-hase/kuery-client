package dev.hsbrysk.kuery.compiler

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Here, we are simply testing whether the compilation succeeds.
 * For detailed behavior, we are testing with functional tests.
 */
@OptIn(ExperimentalCompilerApi::class)
class KueryClientCompilerTest {
    @Test
    fun test() {
        val source = SourceFile.kotlin(
            "Sample.kt",
            """
            import dev.hsbrysk.kuery.core.Sql

            fun main() {
                val userId = 1
                val status = "active"

                Sql.create {
                    +"SELECT * FROM users WHERE user_id = ${'$'}userId AND status = ${'$'}status"
                }.also {
                    println(it)
                }

                Sql.create {
                    add("SELECT * FROM users WHERE user_id = ${'$'}userId AND status = ${'$'}status")
                }.also {
                    println(it)
                }

                Sql.create {
                    val line2 = "L2=${'$'}{bind(2)}"
                    val line1 = "L1=${'$'}{bind(1)}"
                    val line0 = "L0=${'$'}{bind(0)}"

                    addUnsafe(line0)
                    addUnsafe(line1)
                    addUnsafe(line2)
                }.also {
                    println(it)
                }
            }
            """.trimIndent(),
        )

        val result = compile(source)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    private fun compile(vararg sourceFiles: SourceFile): KotlinCompilation.Result {
        return KotlinCompilation().apply {
            sources = sourceFiles.asList()
            commandLineProcessors = listOf(KueryClientCompilerCommandLineProcessor())
            compilerPluginRegistrars = listOf(KueryClientCompilerPluginRegistrar())
            inheritClassPath = true
        }.compile()
    }
}
