package dev.hsbrysk.kuery.compiler

import dev.hsbrysk.kuery.compiler.fir.KueryClientDiagnostics
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CliOptionProcessingException
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

@OptIn(ExperimentalCompilerApi::class)
class KueryClientCompilerCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = PLUGIN_ID
    override val pluginOptions: Collection<AbstractCliOption> = listOf(
        AUTO_TRIM_INDENT_OPTION,
        SQL_SYNTAX_CHECK_OPTION,
        STRICT_OPTION,
    )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        when (option.optionName) {
            AUTO_TRIM_INDENT_OPTION.optionName ->
                configuration.put(AUTO_TRIM_INDENT_KEY, parseBoolean(AUTO_TRIM_INDENT_OPTION_NAME, value))
            SQL_SYNTAX_CHECK_OPTION.optionName ->
                configuration.put(SQL_SYNTAX_CHECK_KEY, parseSqlSyntaxCheck(value))
            STRICT_OPTION.optionName ->
                configuration.put(STRICT_KEY, parseBoolean(STRICT_OPTION_NAME, value))
            else -> error("Unexpected plugin option: ${option.optionName}")
        }
    }

    private fun parseBoolean(
        optionName: String,
        value: String,
    ): Boolean = value.toBooleanStrictOrNull()
        ?: throw CliOptionProcessingException("$optionName must be 'true' or 'false', but was '$value'")

    private fun parseSqlSyntaxCheck(value: String): SqlSyntaxCheck = SqlSyntaxCheck.fromOptionValueOrNull(value)
        ?: throw CliOptionProcessingException(
            "$SQL_SYNTAX_CHECK_OPTION_NAME must be one of ${SqlSyntaxCheck.SUPPORTED_VALUES}, but was '$value'",
        )

    companion object {
        const val PLUGIN_ID = "dev.hsbrysk.kuery-client"
        const val AUTO_TRIM_INDENT_OPTION_NAME = "autoTrimIndent"
        const val SQL_SYNTAX_CHECK_OPTION_NAME = "sqlSyntaxCheck"
        const val STRICT_OPTION_NAME = "strict"

        val AUTO_TRIM_INDENT_KEY: CompilerConfigurationKey<Boolean> =
            CompilerConfigurationKey.create("auto trimIndent")

        // Absent = the check is disabled; present = enabled with the selected strictness.
        val SQL_SYNTAX_CHECK_KEY: CompilerConfigurationKey<SqlSyntaxCheck> =
            CompilerConfigurationKey.create("sql syntax check")

        val STRICT_KEY: CompilerConfigurationKey<Boolean> =
            CompilerConfigurationKey.create("strict SQL safety")

        private val AUTO_TRIM_INDENT_OPTION = CliOption(
            optionName = AUTO_TRIM_INDENT_OPTION_NAME,
            valueDescription = "<true|false>",
            description = "Automatically apply trimIndent to strings passed to SqlBuilder.add/unaryPlus",
            required = false,
            allowMultipleOccurrences = false,
        )
        private val SQL_SYNTAX_CHECK_OPTION = CliOption(
            optionName = SQL_SYNTAX_CHECK_OPTION_NAME,
            valueDescription = "<${SqlSyntaxCheck.SUPPORTED_VALUES}>",
            description = "Validate statically-known SQL in sql blocks: 'generic' for syntax only, or a " +
                "dialect name to also check its feature set",
            required = false,
            allowMultipleOccurrences = false,
        )
        private val STRICT_OPTION = CliOption(
            optionName = STRICT_OPTION_NAME,
            valueDescription = "<true|false>",
            description = "Report the SQL-safety diagnostics " +
                "(${KueryClientDiagnostics.STRICT_DIAGNOSTIC_NAMES.joinToString(", ")}) " +
                "as errors instead of warnings",
            required = false,
            allowMultipleOccurrences = false,
        )
    }
}
