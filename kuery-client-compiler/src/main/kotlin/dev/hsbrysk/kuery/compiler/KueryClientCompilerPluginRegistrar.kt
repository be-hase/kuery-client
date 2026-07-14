package dev.hsbrysk.kuery.compiler

import dev.hsbrysk.kuery.compiler.fir.KueryClientFirExtensionRegistrar
import dev.hsbrysk.kuery.compiler.ir.KueryClientIrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

@ExperimentalCompilerApi
class KueryClientCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String
        get() = KueryClientCompilerCommandLineProcessor.PLUGIN_ID

    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        FirExtensionRegistrarAdapter.registerExtension(KueryClientFirExtensionRegistrar())
        IrGenerationExtension.registerExtension(KueryClientIrGenerationExtension())
    }
}
