package dev.hsbrysk.kuery.compiler

import dev.hsbrysk.kuery.compiler.KueryClientCompilerCommandLineProcessor.Companion.AUTO_TRIM_INDENT_KEY
import dev.hsbrysk.kuery.compiler.KueryClientCompilerCommandLineProcessor.Companion.SQL_SYNTAX_CHECK_KEY
import dev.hsbrysk.kuery.compiler.fir.KueryClientFirExtensionRegistrar
import dev.hsbrysk.kuery.compiler.ir.KueryClientIrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

@ExperimentalCompilerApi
class KueryClientCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String
        get() = KueryClientCompilerCommandLineProcessor.PLUGIN_ID

    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val autoTrimIndent = configuration.get(AUTO_TRIM_INDENT_KEY, false)
        val sqlSyntaxCheck = configuration.get(SQL_SYNTAX_CHECK_KEY, false)
        val messageCollector = configuration.get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        FirExtensionRegistrarAdapter.registerExtension(KueryClientFirExtensionRegistrar(autoTrimIndent, sqlSyntaxCheck))
        IrGenerationExtension.registerExtension(KueryClientIrGenerationExtension(autoTrimIndent, messageCollector))
    }
}
