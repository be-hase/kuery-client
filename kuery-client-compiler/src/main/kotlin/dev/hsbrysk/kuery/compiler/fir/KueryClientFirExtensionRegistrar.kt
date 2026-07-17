package dev.hsbrysk.kuery.compiler.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class KueryClientFirExtensionRegistrar(private val autoTrimIndent: Boolean) : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        val checkersFactory: (FirSession) -> KueryClientFirCheckersExtension = { session ->
            KueryClientFirCheckersExtension(session, autoTrimIndent)
        }
        +checkersFactory
        registerDiagnosticContainers(KueryClientDiagnostics)
    }
}

internal class KueryClientFirCheckersExtension(
    session: FirSession,
    autoTrimIndent: Boolean,
) : FirAdditionalCheckersExtension(session) {
    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        override val functionCallCheckers: Set<FirFunctionCallChecker> = buildSet {
            add(UnsafeSqlStringChecker)
            add(BindCallInSqlTemplateChecker)
            // Only meaningful when the plugin actually auto-trims, so don't even register it
            // otherwise.
            if (autoTrimIndent) {
                add(RedundantTrimIndentChecker)
            }
        }
    }
}
