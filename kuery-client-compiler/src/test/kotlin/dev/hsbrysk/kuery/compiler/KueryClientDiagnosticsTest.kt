package dev.hsbrysk.kuery.compiler

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.compiler.fir.KueryClientDiagnostics
import org.jetbrains.kotlin.diagnostics.AbstractKtDiagnosticFactory
import org.jetbrains.kotlin.diagnostics.Severity
import org.junit.jupiter.api.Test

/**
 * Pins the registered severity of every diagnostic per mode, directly on the container. This is
 * the guard for the name literals inside the container's severity decision: a literal that does
 * not match its property name would silently leave that diagnostic a warning under strict mode,
 * which these assertions catch without a full compilation.
 */
class KueryClientDiagnosticsTest {
    @Test
    fun `all diagnostics default to warnings except the unconditional bind-call error`() {
        val diagnostics = KueryClientDiagnostics(strict = false, userConfiguredWarningLevels = emptySet())

        assertThat(diagnostics.severitiesByName()).isEqualTo(
            mapOf(
                "KUERY_UNSAFE_SQL_STRING" to Severity.WARNING,
                "KUERY_BIND_CALL_IN_SQL_TEMPLATE" to Severity.ERROR,
                "KUERY_REDUNDANT_TRIM_INDENT" to Severity.WARNING,
                "KUERY_SQL_SYNTAX" to Severity.WARNING,
                "KUERY_SQL_DIALECT" to Severity.WARNING,
            ),
        )
    }

    @Test
    fun `strict mode escalates exactly the strict diagnostic names`() {
        val diagnostics = KueryClientDiagnostics(strict = true, userConfiguredWarningLevels = emptySet())

        assertThat(diagnostics.severitiesByName()).isEqualTo(
            mapOf(
                "KUERY_UNSAFE_SQL_STRING" to Severity.ERROR,
                "KUERY_BIND_CALL_IN_SQL_TEMPLATE" to Severity.ERROR,
                "KUERY_REDUNDANT_TRIM_INDENT" to Severity.WARNING,
                "KUERY_SQL_SYNTAX" to Severity.ERROR,
                "KUERY_SQL_DIALECT" to Severity.ERROR,
            ),
        )
    }

    @Test
    fun `a user-configured diagnostic keeps its warning registration under strict mode`() {
        for (name in KueryClientDiagnostics.STRICT_DIAGNOSTIC_NAMES) {
            val diagnostics = KueryClientDiagnostics(strict = true, userConfiguredWarningLevels = setOf(name))

            assertThat(diagnostics.severitiesByName()[name]).isEqualTo(Severity.WARNING)
        }
    }

    private fun KueryClientDiagnostics.severitiesByName(): Map<String, Severity> = listOf(
        KUERY_UNSAFE_SQL_STRING,
        KUERY_BIND_CALL_IN_SQL_TEMPLATE,
        KUERY_REDUNDANT_TRIM_INDENT,
        KUERY_SQL_SYNTAX,
        KUERY_SQL_DIALECT,
    ).associate { it.nameAndSeverity() }

    private fun AbstractKtDiagnosticFactory.nameAndSeverity(): Pair<String, Severity> = name to severity
}
