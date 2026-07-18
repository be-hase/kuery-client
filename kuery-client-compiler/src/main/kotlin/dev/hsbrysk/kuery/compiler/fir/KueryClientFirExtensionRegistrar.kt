package dev.hsbrysk.kuery.compiler.fir

import dev.hsbrysk.kuery.compiler.SqlSyntaxCheck
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class KueryClientFirExtensionRegistrar(
    private val autoTrimIndent: Boolean,
    private val sqlSyntaxCheck: SqlSyntaxCheck?,
) : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        val checkersFactory: (FirSession) -> KueryClientFirCheckersExtension = { session ->
            KueryClientFirCheckersExtension(session, autoTrimIndent, sqlSyntaxCheck)
        }
        +checkersFactory
        registerDiagnosticContainers(KueryClientDiagnostics)
    }
}

internal class KueryClientFirCheckersExtension(
    session: FirSession,
    autoTrimIndent: Boolean,
    sqlSyntaxCheck: SqlSyntaxCheck?,
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
            // Opt-in (null = disabled); the checker must know autoTrimIndent to reconstruct the
            // runtime SQL text.
            if (sqlSyntaxCheck != null) {
                add(SqlSyntaxChecker(autoTrimIndent, sqlSyntaxCheck))
            }
        }
    }
}
