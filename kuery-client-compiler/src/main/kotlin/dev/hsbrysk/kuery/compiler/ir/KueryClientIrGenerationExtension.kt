package dev.hsbrysk.kuery.compiler.ir

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

class KueryClientIrGenerationExtension(
    private val autoTrimIndent: Boolean,
    private val messageCollector: MessageCollector,
) : IrGenerationExtension {
    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
    ) {
        // Inject first: the injection only wraps the block argument of sql(...) calls and never
        // touches the add/unaryPlus calls inside the block, so the passes do not interfere.
        moduleFragment.transformChildren(SqlIdInjectionTransformer(pluginContext, messageCollector), null)
        moduleFragment.transformChildren(StringInterpolationTransformer(pluginContext, autoTrimIndent), null)
    }
}
