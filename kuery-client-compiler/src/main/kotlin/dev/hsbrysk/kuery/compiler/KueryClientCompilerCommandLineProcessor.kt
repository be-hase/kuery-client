package dev.hsbrysk.kuery.compiler

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

@OptIn(ExperimentalCompilerApi::class)
class KueryClientCompilerCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = PLUGIN_ID
    override val pluginOptions: Collection<AbstractCliOption> = listOf(AUTO_TRIM_INDENT_OPTION)

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        when (option.optionName) {
            AUTO_TRIM_INDENT_OPTION.optionName ->
                configuration.put(AUTO_TRIM_INDENT_KEY, value.toBooleanStrict())
            else -> error("Unexpected plugin option: ${option.optionName}")
        }
    }

    companion object {
        const val PLUGIN_ID = "dev.hsbrysk.kuery-client"
        const val AUTO_TRIM_INDENT_OPTION_NAME = "autoTrimIndent"

        val AUTO_TRIM_INDENT_KEY: CompilerConfigurationKey<Boolean> =
            CompilerConfigurationKey.create("auto trimIndent")

        private val AUTO_TRIM_INDENT_OPTION = CliOption(
            optionName = AUTO_TRIM_INDENT_OPTION_NAME,
            valueDescription = "<true|false>",
            description = "Automatically apply trimIndent to strings passed to SqlBuilder.add/unaryPlus",
            required = false,
            allowMultipleOccurrences = false,
        )
    }
}
