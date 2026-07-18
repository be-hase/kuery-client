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
    private val strict: Boolean,
    private val userConfiguredWarningLevels: Set<String>,
) : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        // The diagnostics container is per compilation: the strict option decides whether the
        // SQL-safety diagnostics are registered as warnings or errors.
        val diagnostics = KueryClientDiagnostics(strict, userConfiguredWarningLevels)
        val checkersFactory: (FirSession) -> KueryClientFirCheckersExtension = { session ->
            KueryClientFirCheckersExtension(session, autoTrimIndent, sqlSyntaxCheck, diagnostics)
        }
        +checkersFactory
        registerDiagnosticContainers(diagnostics)
    }
}

internal class KueryClientFirCheckersExtension(
    session: FirSession,
    autoTrimIndent: Boolean,
    sqlSyntaxCheck: SqlSyntaxCheck?,
    diagnostics: KueryClientDiagnostics,
) : FirAdditionalCheckersExtension(session) {
    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        override val functionCallCheckers: Set<FirFunctionCallChecker> = buildSet {
            add(UnsafeSqlStringChecker(diagnostics))
            add(BindCallInSqlTemplateChecker(diagnostics))
            // Only meaningful when the plugin actually auto-trims, so don't even register it
            // otherwise.
            if (autoTrimIndent) {
                add(RedundantTrimIndentChecker(diagnostics))
            }
            // Opt-in (null = disabled); the checker must know autoTrimIndent to reconstruct the
            // runtime SQL text.
            if (sqlSyntaxCheck != null) {
                add(SqlSyntaxChecker(autoTrimIndent, sqlSyntaxCheck, diagnostics))
            }
        }
    }
}
