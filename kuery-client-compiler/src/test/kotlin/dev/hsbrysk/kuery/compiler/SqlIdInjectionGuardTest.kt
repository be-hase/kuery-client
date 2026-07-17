package dev.hsbrysk.kuery.compiler

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Verifies the sqlId injection's compile-level guards, which the functional-test module cannot
 * observe: whether the rewrite is emitted at all (constant-pool scan, as in
 * AutoTrimIndentBytecodeTest) and the diagnostics around it. The behavior of injected ids is
 * covered by the functional-test module's SqlIdInjectionTest.
 */
@OptIn(ExperimentalCompilerApi::class)
class SqlIdInjectionGuardTest {
    @Test
    fun `a literal block call is rewritten to reference the wrapper factory`() {
        // when
        val result = compile(CALL_SITE_SOURCE)

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.classesReference("sqlIdProvidingBlock")).isTrue()
    }

    @Test
    fun `a non-literal block argument is left untouched`() {
        // given: the block is passed as a variable, so mocking-library matchers and
        // pass-through code never get wrapped
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.KueryBlockingClient
            import dev.hsbrysk.kuery.core.SqlBuilder

            fun find(client: KueryBlockingClient, block: SqlBuilder.() -> Unit) {
                client.sql(block)
            }
            """.trimIndent(),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.classesReference("sqlIdProvidingBlock")).isFalse()
    }

    @Test
    fun `a stale core without the wrapper factory disables injection with a warning`() {
        // given: a "core" that has the client interfaces but predates sqlIdProvidingBlock
        val staleCore = compileStaleCore()
        assertThat(staleCore.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)

        // when
        val result = KotlinCompilation().apply {
            sources = listOf(SourceFile.kotlin("Sample.kt", CALL_SITE_SOURCE))
            compilerPluginRegistrars = listOf(KueryClientCompilerPluginRegistrar())
            inheritClassPath = false
            classpaths = listOf(staleCore.outputDirectory)
            verbose = false
        }.compile()

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.classesReference("sqlIdProvidingBlock")).isFalse()
        assertThat(result.messages).contains("sqlId injection is disabled")
    }

    @Test
    fun `a stale core produces no warning when nothing would have been rewritten`() {
        // given
        val staleCore = compileStaleCore()

        // when: the only sql call passes a variable, which is never rewritten anyway
        val result = KotlinCompilation().apply {
            sources = listOf(
                SourceFile.kotlin(
                    "Sample.kt",
                    """
                    import dev.hsbrysk.kuery.core.KueryBlockingClient
                    import dev.hsbrysk.kuery.core.SqlBuilder

                    fun find(client: KueryBlockingClient, block: SqlBuilder.() -> Unit) {
                        client.sql(block)
                    }
                    """.trimIndent(),
                ),
            )
            compilerPluginRegistrars = listOf(KueryClientCompilerPluginRegistrar())
            inheritClassPath = false
            classpaths = listOf(staleCore.outputDirectory)
            verbose = false
        }.compile()

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).doesNotContain("sqlId injection is disabled")
    }

    // A minimal stand-in for a pre-injection kuery-client-core: the same client FQNs, no
    // dev.hsbrysk.kuery.core.internal.sqlIdProvidingBlock.
    private fun compileStaleCore(): JvmCompilationResult = KotlinCompilation().apply {
        sources = listOf(
            SourceFile.kotlin(
                "StaleCore.kt",
                """
                package dev.hsbrysk.kuery.core

                interface SqlBuilder

                interface KueryBlockingClient {
                    fun sql(sqlId: String, block: SqlBuilder.() -> Unit): FetchSpec

                    fun sql(block: SqlBuilder.() -> Unit): FetchSpec

                    interface FetchSpec
                }
                """.trimIndent(),
            ),
        )
        inheritClassPath = false
        verbose = false
    }.compile()

    // The constant pool stores referenced method names as plain modified-UTF-8 entries, so a
    // simple byte scan is enough to tell whether any generated class calls the factory.
    private fun JvmCompilationResult.classesReference(name: String): Boolean = outputDirectory
        .walkTopDown()
        .filter { it.isFile && it.extension == "class" }
        .also { check(it.any()) { "no classes were generated" } }
        .any { String(it.readBytes(), Charsets.ISO_8859_1).contains(name) }

    companion object {
        private val CALL_SITE_SOURCE = """
            import dev.hsbrysk.kuery.core.KueryBlockingClient

            fun find(client: KueryBlockingClient) {
                client.sql { }
            }
        """.trimIndent()
    }
}
