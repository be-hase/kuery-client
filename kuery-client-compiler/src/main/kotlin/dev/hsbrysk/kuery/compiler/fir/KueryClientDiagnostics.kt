package dev.hsbrysk.kuery.compiler.fir

import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.warning0
import org.jetbrains.kotlin.psi.KtExpression

internal object KueryClientDiagnostics : KtDiagnosticsContainer() {
    // The property name is the diagnostic name, i.e. the key for @Suppress and -Xwarning-level.
    val KUERY_UNSAFE_SQL_STRING by warning0<KtExpression>(SourceElementPositioningStrategies.DEFAULT)

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = KueryClientDiagnosticRenderers
}

internal object KueryClientDiagnosticRenderers : BaseDiagnosticRendererFactory() {
    @Suppress("ktlint:standard:property-naming")
    override val MAP by KtDiagnosticFactoryToRendererMap("KueryClient") { map ->
        map.put(
            KueryClientDiagnostics.KUERY_UNSAFE_SQL_STRING,
            "This expression is not a string literal/template, so the kuery-client compiler plugin cannot " +
                "convert its string interpolation into bind parameters; it will be executed as raw SQL " +
                "(SQL injection risk). Pass a string literal/template directly, or use addUnsafe() with bind() " +
                "for dynamically built SQL. If this is intentional, annotate the enclosing declaration with " +
                "@Suppress(\"KUERY_UNSAFE_SQL_STRING\").",
        )
    }
}
