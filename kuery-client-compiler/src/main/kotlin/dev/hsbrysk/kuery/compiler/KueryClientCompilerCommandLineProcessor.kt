package dev.hsbrysk.kuery.compiler

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
        SQL_SYNTAX_CHECK_DIALECT_OPTION,
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
                configuration.put(SQL_SYNTAX_CHECK_KEY, parseBoolean(SQL_SYNTAX_CHECK_OPTION_NAME, value))
            SQL_SYNTAX_CHECK_DIALECT_OPTION.optionName ->
                configuration.put(SQL_SYNTAX_CHECK_DIALECT_KEY, parseSqlDialect(value))
            else -> error("Unexpected plugin option: ${option.optionName}")
        }
    }

    private fun parseBoolean(
        optionName: String,
        value: String,
    ): Boolean = value.toBooleanStrictOrNull()
        ?: throw CliOptionProcessingException("$optionName must be 'true' or 'false', but was '$value'")

    private fun parseSqlDialect(value: String): SqlDialect =
        SqlDialect.entries.find { it.optionValue.equals(value, ignoreCase = true) }
            ?: throw CliOptionProcessingException(
                "$SQL_SYNTAX_CHECK_DIALECT_OPTION_NAME must be one of $SUPPORTED_DIALECTS, but was '$value'",
            )

    companion object {
        const val PLUGIN_ID = "dev.hsbrysk.kuery-client"
        const val AUTO_TRIM_INDENT_OPTION_NAME = "autoTrimIndent"
        const val SQL_SYNTAX_CHECK_OPTION_NAME = "sqlSyntaxCheck"
        const val SQL_SYNTAX_CHECK_DIALECT_OPTION_NAME = "sqlSyntaxCheckDialect"

        // Derived from the enum so the accepted and the advertised value sets cannot drift.
        val SUPPORTED_DIALECTS: String = SqlDialect.entries.joinToString(", ") { it.optionValue }

        val AUTO_TRIM_INDENT_KEY: CompilerConfigurationKey<Boolean> =
            CompilerConfigurationKey.create("auto trimIndent")
        val SQL_SYNTAX_CHECK_KEY: CompilerConfigurationKey<Boolean> =
            CompilerConfigurationKey.create("sql syntax check")
        val SQL_SYNTAX_CHECK_DIALECT_KEY: CompilerConfigurationKey<SqlDialect> =
            CompilerConfigurationKey.create("sql syntax check dialect")

        private val AUTO_TRIM_INDENT_OPTION = CliOption(
            optionName = AUTO_TRIM_INDENT_OPTION_NAME,
            valueDescription = "<true|false>",
            description = "Automatically apply trimIndent to strings passed to SqlBuilder.add/unaryPlus",
            required = false,
            allowMultipleOccurrences = false,
        )
        private val SQL_SYNTAX_CHECK_OPTION = CliOption(
            optionName = SQL_SYNTAX_CHECK_OPTION_NAME,
            valueDescription = "<true|false>",
            description = "Validate statically-known SQL in sql blocks with a SQL parser and warn on syntax errors",
            required = false,
            allowMultipleOccurrences = false,
        )
        private val SQL_SYNTAX_CHECK_DIALECT_OPTION = CliOption(
            optionName = SQL_SYNTAX_CHECK_DIALECT_OPTION_NAME,
            valueDescription = "<$SUPPORTED_DIALECTS>",
            description = "Additionally check the SQL against this dialect's feature set (implies sqlSyntaxCheck)",
            required = false,
            allowMultipleOccurrences = false,
        )
    }
}
