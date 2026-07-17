package dev.hsbrysk.kuery.compiler.fir

import dev.hsbrysk.kuery.compiler.SqlDialect
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class KueryClientFirExtensionRegistrar(
    private val autoTrimIndent: Boolean,
    private val sqlSyntaxCheck: Boolean,
    private val sqlSyntaxCheckDialect: SqlDialect? = null,
) : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        val checkersFactory: (FirSession) -> KueryClientFirCheckersExtension = { session ->
            KueryClientFirCheckersExtension(session, autoTrimIndent, sqlSyntaxCheck, sqlSyntaxCheckDialect)
        }
        +checkersFactory
        registerDiagnosticContainers(KueryClientDiagnostics)
    }
}

internal class KueryClientFirCheckersExtension(
    session: FirSession,
    autoTrimIndent: Boolean,
    sqlSyntaxCheck: Boolean,
    sqlSyntaxCheckDialect: SqlDialect?,
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
            // Opt-in; the checker must know autoTrimIndent to reconstruct the runtime SQL text.
            if (sqlSyntaxCheck) {
                add(SqlSyntaxChecker(autoTrimIndent, sqlSyntaxCheckDialect))
            }
        }
    }
}
