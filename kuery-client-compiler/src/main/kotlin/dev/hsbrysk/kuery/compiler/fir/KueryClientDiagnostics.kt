package dev.hsbrysk.kuery.compiler.fir

import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.error0
import org.jetbrains.kotlin.diagnostics.error1
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers
import org.jetbrains.kotlin.diagnostics.warning0
import org.jetbrains.kotlin.diagnostics.warning1
import org.jetbrains.kotlin.psi.KtExpression

/**
 * One instance exists per compilation (created by [KueryClientFirExtensionRegistrar]), because
 * the severity of the SQL-safety diagnostics is decided per compilation: under the `strict`
 * plugin option they are registered as errors instead of warnings — except the ones the user
 * explicitly configured with `-Xwarning-level`, which keep their warning registration so the
 * user's chosen level applies (strict only changes the default). The diagnostic NAME is the
 * same at either severity — the property name is the key for `@Suppress` and `-Xwarning-level`,
 * and both keep working identically.
 */
// The SCREAMING_SNAKE property names are the diagnostic names themselves and must stay as-is.
@Suppress("VariableNaming", "ktlint:standard:property-naming")
internal class KueryClientDiagnostics(
    strict: Boolean,
    userConfiguredWarningLevels: Set<String>,
) : KtDiagnosticsContainer() {
    // Declared before the factory properties: their provideDelegate links each factory to the
    // container's renderer factory, i.e. getRendererFactory() is called from this class's own
    // constructor, so this field must already be initialized by then. Constructing the renderers
    // that early is safe — its MAP is a Lazy built on first rendering, after every factory below
    // is initialized.
    private val rendererFactory = KueryClientDiagnosticRenderers(this)

    // Whether the diagnostic with the given name is registered as an error. The name literal at
    // each call site must match the property name it decides for (the property name is the
    // diagnostic name); KueryClientDiagnosticsTest pins the severity of every diagnostic per
    // mode, so a mismatch cannot go unnoticed.
    private val escalate: (String) -> Boolean = { name ->
        strict && name in STRICT_DIAGNOSTIC_NAMES && name !in userConfiguredWarningLevels
    }

    val KUERY_UNSAFE_SQL_STRING by (
        if (escalate("KUERY_UNSAFE_SQL_STRING")) {
            error0<KtExpression>(SourceElementPositioningStrategies.DEFAULT)
        } else {
            warning0<KtExpression>(SourceElementPositioningStrategies.DEFAULT)
        }
        )

    // An error regardless of strict mode: SQL that fires this always compares against the
    // literal string ":pN" at runtime, so there is no valid program in which it may be left
    // as-is.
    val KUERY_BIND_CALL_IN_SQL_TEMPLATE by error0<KtExpression>(SourceElementPositioningStrategies.DEFAULT)

    val KUERY_REDUNDANT_TRIM_INDENT by (
        if (escalate("KUERY_REDUNDANT_TRIM_INDENT")) {
            error0<KtExpression>(SourceElementPositioningStrategies.DEFAULT)
        } else {
            warning0<KtExpression>(SourceElementPositioningStrategies.DEFAULT)
        }
        )

    val KUERY_SQL_SYNTAX by (
        if (escalate("KUERY_SQL_SYNTAX")) {
            error1<KtExpression, String>(SourceElementPositioningStrategies.DEFAULT)
        } else {
            warning1<KtExpression, String>(SourceElementPositioningStrategies.DEFAULT)
        }
        )

    val KUERY_SQL_DIALECT by (
        if (escalate("KUERY_SQL_DIALECT")) {
            error1<KtExpression, String>(SourceElementPositioningStrategies.DEFAULT)
        } else {
            warning1<KtExpression, String>(SourceElementPositioningStrategies.DEFAULT)
        }
        )

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = rendererFactory

    companion object {
        // The single source of truth for which diagnostics the strict option escalates to
        // errors. Membership here is the decision — every warning-default property above routes
        // through [escalate] — and the CLI option description is derived from it too.
        // KUERY_REDUNDANT_TRIM_INDENT is deliberately absent (a style issue, not a safety
        // issue); KUERY_BIND_CALL_IN_SQL_TEMPLATE is an unconditional error.
        val STRICT_DIAGNOSTIC_NAMES: Set<String> = setOf(
            "KUERY_UNSAFE_SQL_STRING",
            "KUERY_SQL_SYNTAX",
            "KUERY_SQL_DIALECT",
        )
    }
}

internal class KueryClientDiagnosticRenderers(diagnostics: KueryClientDiagnostics) : BaseDiagnosticRendererFactory() {
    @Suppress("ktlint:standard:property-naming")
    override val MAP by KtDiagnosticFactoryToRendererMap("KueryClient") { map ->
        map.put(
            diagnostics.KUERY_UNSAFE_SQL_STRING,
            "This expression is not a string literal/template, so the kuery-client compiler plugin cannot " +
                "guarantee that its string interpolation is converted into bind parameters; it may be executed " +
                "as raw SQL (SQL injection risk). Pass a string literal/template directly, or use addUnsafe() " +
                "with bind() for dynamically built SQL. If this is intentional, annotate the enclosing " +
                "declaration with @Suppress(\"KUERY_UNSAFE_SQL_STRING\").",
        )
        map.put(
            diagnostics.KUERY_REDUNDANT_TRIM_INDENT,
            // Describes the behavior of StringInterpolationTransformer.autoTrimmedArgument —
            // keep the two in sync.
            "This trimIndent() is redundant: autoTrimIndent is enabled, so the kuery-client compiler plugin " +
                "already applies trimIndent to every string passed to add()/unaryPlus. The explicit call " +
                "only adds an extra runtime trim — and on a string literal/template it also prevents the " +
                "compile-time trimming. Remove the trimIndent() call. If this is intentional, annotate the " +
                "enclosing declaration with @Suppress(\"KUERY_REDUNDANT_TRIM_INDENT\").",
        )
        map.put(
            diagnostics.KUERY_BIND_CALL_IN_SQL_TEMPLATE,
            "bind() must not be called inside a string template passed to add()/unaryPlus. The compiler " +
                "plugin already converts every interpolated value into a bind parameter, so the placeholder " +
                "name returned by bind() would itself be re-bound as a new parameter value and the SQL would " +
                "compare against the literal string ':pN'. Interpolate the value directly (\$value instead " +
                "of \${bind(value)}), or use addUnsafe() when you need bind().",
        )
        map.put(
            diagnostics.KUERY_SQL_SYNTAX,
            "The SQL assembled from this block failed to parse: {0} The check uses a generic SQL parser " +
                "(JSqlParser), which does not know every vendor-specific syntax, so this may be a false " +
                "positive. If the SQL is valid, annotate the enclosing declaration with " +
                "@Suppress(\"KUERY_SQL_SYNTAX\").",
            CommonRenderers.STRING,
        )
        map.put(
            diagnostics.KUERY_SQL_DIALECT,
            "The SQL assembled from this block uses a feature the configured dialect does not support: {0} " +
                "The dialect check is a feature-level allow-list (JSqlParser), so this may be a false " +
                "positive. If the SQL is valid for your database, annotate the enclosing declaration with " +
                "@Suppress(\"KUERY_SQL_DIALECT\").",
            CommonRenderers.STRING,
        )
    }
}
