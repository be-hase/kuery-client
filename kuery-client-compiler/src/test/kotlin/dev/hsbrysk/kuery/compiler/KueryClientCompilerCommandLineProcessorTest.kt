package dev.hsbrysk.kuery.compiler

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@OptIn(ExperimentalCompilerApi::class)
class KueryClientCompilerCommandLineProcessorTest {
    @ParameterizedTest
    @ValueSource(strings = ["true", "false"])
    fun `canonical boolean values are accepted`(value: String) {
        // when
        val result = compileWithOptionValue(value)

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `an invalid option value fails with an error naming the option`() {
        // when
        val result = compileWithOptionValue("True")

        // then
        assertThat(result.exitCode).isNotEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains("autoTrimIndent must be 'true' or 'false', but was 'True'")
    }

    private fun compileWithOptionValue(value: String): JvmCompilationResult = compile(
        source = "fun unused() {}",
        pluginOptions = listOf(autoTrimIndentOption(value)),
    )
}
