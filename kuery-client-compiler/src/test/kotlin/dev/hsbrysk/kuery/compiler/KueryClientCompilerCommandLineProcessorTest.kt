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

    @ParameterizedTest
    @ValueSource(strings = ["ansi", "oracle", "mysql", "sqlserver", "mariadb", "postgresql", "h2", "MySQL"])
    fun `known dialect values are accepted case-insensitively`(value: String) {
        // when
        val result = compile(
            source = "fun unused() {}",
            pluginOptions = listOf(sqlSyntaxCheckDialectOption(value)),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `an unknown dialect value fails with an error listing the supported dialects`() {
        // when
        val result = compile(
            source = "fun unused() {}",
            pluginOptions = listOf(sqlSyntaxCheckDialectOption("db2")),
        )

        // then
        assertThat(result.exitCode).isNotEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(
            "sqlSyntaxCheckDialect must be one of ansi, oracle, mysql, sqlserver, mariadb, postgresql, h2, " +
                "but was 'db2'",
        )
    }

    private fun compileWithOptionValue(value: String): JvmCompilationResult = compile(
        source = "fun unused() {}",
        pluginOptions = listOf(autoTrimIndentOption(value)),
    )
}
