package dev.hsbrysk.kuery.compiler

import net.sf.jsqlparser.util.validation.feature.DatabaseType
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
                configuration.put(SQL_SYNTAX_CHECK_DIALECT_KEY, parseDatabaseType(value))
            else -> error("Unexpected plugin option: ${option.optionName}")
        }
    }

    private fun parseBoolean(
        optionName: String,
        value: String,
    ): Boolean = value.toBooleanStrictOrNull()
        ?: throw CliOptionProcessingException("$optionName must be 'true' or 'false', but was '$value'")

    private fun parseDatabaseType(value: String): DatabaseType {
        val enumName = if (value.equals("ansi", ignoreCase = true)) "ANSI_SQL" else value.uppercase()
        return DatabaseType.entries.find { it.name == enumName }
            ?: throw CliOptionProcessingException(
                "$SQL_SYNTAX_CHECK_DIALECT_OPTION_NAME must be one of $SUPPORTED_DIALECTS, but was '$value'",
            )
    }

    companion object {
        const val PLUGIN_ID = "dev.hsbrysk.kuery-client"
        const val AUTO_TRIM_INDENT_OPTION_NAME = "autoTrimIndent"
        const val SQL_SYNTAX_CHECK_OPTION_NAME = "sqlSyntaxCheck"
        const val SQL_SYNTAX_CHECK_DIALECT_OPTION_NAME = "sqlSyntaxCheckDialect"
        const val SUPPORTED_DIALECTS = "ansi, oracle, mysql, sqlserver, mariadb, postgresql, h2"

        val AUTO_TRIM_INDENT_KEY: CompilerConfigurationKey<Boolean> =
            CompilerConfigurationKey.create("auto trimIndent")
        val SQL_SYNTAX_CHECK_KEY: CompilerConfigurationKey<Boolean> =
            CompilerConfigurationKey.create("sql syntax check")
        val SQL_SYNTAX_CHECK_DIALECT_KEY: CompilerConfigurationKey<DatabaseType> =
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
