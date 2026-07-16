package dev.hsbrysk.kuery.compiler

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.PluginOption
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@OptIn(ExperimentalCompilerApi::class)
class KueryClientCompilerCommandLineProcessorTest {
    @ParameterizedTest
    @ValueSource(strings = ["true", "false"])
    fun `canonical boolean values are accepted`(value: String) {
        val result = compileWithOptionValue(value)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `an invalid option value fails with an error naming the option`() {
        val result = compileWithOptionValue("True")
        assertThat(result.exitCode).isNotEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains("autoTrimIndent must be 'true' or 'false', but was 'True'")
    }

    private fun compileWithOptionValue(value: String): JvmCompilationResult = compile(
        source = "fun unused() {}",
        pluginOptions = listOf(
            PluginOption(
                KueryClientCompilerCommandLineProcessor.PLUGIN_ID,
                KueryClientCompilerCommandLineProcessor.AUTO_TRIM_INDENT_OPTION_NAME,
                value,
            ),
        ),
    )
}
