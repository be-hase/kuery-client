package dev.hsbrysk.kuery.compiler

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import dev.hsbrysk.kuery.core.sql2
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class KueryClientCompilerTest {
    @Test
    fun test() {
        val source = SourceFile.kotlin(
            "Sample.kt",
            """
            import dev.hsbrysk.kuery.core.sql2

            fun main() {
                val variable = "variable"
                sql2 {
                    add("literal ${'$'}variable ${'$'}{null}")
                    // add("literal ${'$'}variable".removePrefix("l"))
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

fun main() {
    val hoge = "hoge"
    sql2 {
        add("literal $hoge")
        add("literal $hoge".removePrefix("l"))
    }
}
