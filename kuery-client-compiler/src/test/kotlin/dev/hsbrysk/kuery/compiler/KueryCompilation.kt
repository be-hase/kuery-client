package dev.hsbrysk.kuery.compiler

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.PluginOption
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

// The plugin option triples in one place, so tests cannot drift from the real option wiring.
internal fun autoTrimIndentOption(value: String = "true"): PluginOption = PluginOption(
    KueryClientCompilerCommandLineProcessor.PLUGIN_ID,
    KueryClientCompilerCommandLineProcessor.AUTO_TRIM_INDENT_OPTION_NAME,
    value,
)

internal fun sqlSyntaxCheckOption(value: String = "true"): PluginOption = PluginOption(
    KueryClientCompilerCommandLineProcessor.PLUGIN_ID,
    KueryClientCompilerCommandLineProcessor.SQL_SYNTAX_CHECK_OPTION_NAME,
    value,
)

internal fun sqlSyntaxCheckDialectOption(value: String): PluginOption = PluginOption(
    KueryClientCompilerCommandLineProcessor.PLUGIN_ID,
    KueryClientCompilerCommandLineProcessor.SQL_SYNTAX_CHECK_DIALECT_OPTION_NAME,
    value,
)

// Single kotlin-compile-testing harness with the kuery-client plugin applied, shared by every
// test that compiles a snippet, so the registrar/processor wiring lives in one place.
@OptIn(ExperimentalCompilerApi::class)
internal fun compile(
    source: String,
    allWarningsAsErrors: Boolean = false,
    pluginOptions: List<PluginOption> = emptyList(),
): JvmCompilationResult = KotlinCompilation().apply {
    sources = listOf(SourceFile.kotlin("Sample.kt", source))
    commandLineProcessors = listOf(KueryClientCompilerCommandLineProcessor())
    compilerPluginRegistrars = listOf(KueryClientCompilerPluginRegistrar())
    this.pluginOptions = pluginOptions
    inheritClassPath = true
    verbose = false
    this.allWarningsAsErrors = allWarningsAsErrors
}.compile()
