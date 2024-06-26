package dev.hsbrysk.kuery.compiler

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class)
class KueryClientCompilerCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = "dev.hsbrysk.kuery-client"
    override val pluginOptions: Collection<AbstractCliOption> = listOf()

    // NOOP (There are no plugin options)
}
