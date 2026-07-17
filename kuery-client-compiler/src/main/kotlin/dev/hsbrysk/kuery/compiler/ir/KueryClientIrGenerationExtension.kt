package dev.hsbrysk.kuery.compiler.ir

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

class KueryClientIrGenerationExtension(private val autoTrimIndent: Boolean) : IrGenerationExtension {
    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
    ) {
        moduleFragment.transformChildren(StringInterpolationTransformer(pluginContext, autoTrimIndent), null)
    }
}
