package dev.hsbrysk.kuery.compiler

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

// How add/unaryPlus arguments are reconstructed into the runtime SQL text (templates, consts,
// trims, multi-line strings); entry-point and skip behavior live in SqlSyntaxCheckerTest.
@OptIn(ExperimentalCompilerApi::class)
class SqlSyntaxCheckerReconstructionTest {
    @Test
    fun `no warning for a valid multi-line SQL string`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int) {
                Sql {
                    add(
                        ${TRIPLE_QUOTE}
                        SELECT *
                        FROM users
                        WHERE id = ${'$'}id
                        ${TRIPLE_QUOTE}.trimIndent(),
                    )
                }
            }
            """.trimIndent(),
            allWarningsAsErrors = true,
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_NAME)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `the warning is anchored to the failing add when the block mixes multi-line and single-line parts`() {
        // The reconstructed SQL spans several lines per part, so the failing line must be mapped
        // back to the right add across the multi-line accumulation.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int) {
                Sql {
                    +${TRIPLE_QUOTE}
                        SELECT *
                        FROM users
                        ${TRIPLE_QUOTE}.trimIndent()
                    +"WHRE id = ${'$'}id"
                }
            }
            """.trimIndent(),
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        // The broken single-line fragment is on line 9 of the compiled source.
        assertThat(result.messages).contains("Sample.kt:9")
    }

    @Test
    fun `warn under autoTrimIndent when the assembled SQL does not parse`() {
        // The checker trims each part the same way the plugin does at runtime, so the check must
        // still fire when both options are enabled.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query() {
                Sql {
                    +${TRIPLE_QUOTE}
                        SELCT *
                        FROM users
                    ${TRIPLE_QUOTE}
                }
            }
            """.trimIndent(),
            pluginOptions = listOf(sqlSyntaxCheckOption(), autoTrimIndentOption()),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    @Test
    fun `the reconstructed text of a trimMargin argument is the margin-stripped SQL`() {
        // The margin prefix must be stripped exactly as at runtime: `|SELCT ...` parses (with the
        // pipe kept it would fail on the pipe, not on the typo), so a broken statement built with
        // trimMargin is still flagged for the right reason.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query() {
                Sql { add("|SELCT 1".trimMargin()) }
            }
            """.trimIndent(),
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
        // The reported token is the typo, proving the leading '|' was removed before parsing.
        assertThat(result.messages).contains("SELCT")
    }

    @Test
    fun `a blank trimMargin prefix does not crash the compiler`() {
        // trimMargin("") throws IllegalArgumentException; the reconstruction must skip such a
        // block instead of letting the exception escape the checker as an INTERNAL_ERROR. (The
        // user code also throws at runtime — Kotlin itself warns about the blank prefix — so
        // there is no SQL to validate anyway.)
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query() {
                Sql { add("SELECT 1".trimMargin("")) }
            }
            """.trimIndent(),
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_NAME)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `no warning when a const val initializer is not a single literal`() {
        // FIR2IR constant-folds such consts into SQL text, but the checker cannot compute the
        // value at FIR time, so the block must be skipped instead of guessing a placeholder.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            const val USERS = "users"
            const val TABLE = "app_" + USERS
            const val ALIAS = USERS

            fun query(id: Int) {
                Sql { +"SELECT * FROM ${'$'}TABLE WHERE id = ${'$'}id" }
                Sql { +"SELECT * FROM ${'$'}ALIAS WHERE id = ${'$'}id" }
            }
            """.trimIndent(),
            allWarningsAsErrors = true,
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_NAME)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `warn when a template with a non-string const accompanies broken SQL`() {
        // A non-String const is bound as :pN at runtime (not inlined as text), so the block is
        // still statically known and must be checked.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            const val LIMIT_CONST = 10

            fun query() {
                Sql { +"SELCT * FROM users LIMIT ${'$'}LIMIT_CONST" }
            }
            """.trimIndent(),
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    @Test
    fun `no warning when a Java constant appears in a template`() {
        // FIR2IR folds Java compile-time constants into SQL text (like Kotlin const vals), so
        // the runtime SQL is "SELECT * FROM xml" — valid.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query() {
                Sql { +"SELECT * FROM ${'$'}{javax.xml.XMLConstants.XML_NS_PREFIX}" }
            }
            """.trimIndent(),
            allWarningsAsErrors = true,
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_NAME)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `no warning when a template value mutates the builder`() {
        // The value expression runs before the enclosing add at runtime and emits its own line,
        // so the block's SQL is not what the template alone suggests — it must be skipped.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql
            import dev.hsbrysk.kuery.core.SqlBuilder

            fun SqlBuilder.header(): Int {
                +"SELECT * FROM users"
                return 1
            }

            fun query() {
                Sql { +"WHERE id = ${'$'}{header()}" }
            }
            """.trimIndent(),
            allWarningsAsErrors = true,
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_NAME)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `warn when the whole string is a single interpolated variable`() {
        // The fragment is bound, not spliced: the runtime SQL body is literally ":p0", which is
        // genuinely broken — this warning catches a real misuse of interpolation.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(fragment: String) {
                Sql { +"${'$'}fragment" }
            }
            """.trimIndent(),
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    @Test
    fun `no warning for surrounding unicode whitespace that the runtime trims away`() {
        // DefaultSqlBuilder.build() trims the assembled body, so the executed SQL is a plain
        // SELECT; the checker must mirror that trim instead of feeding U+2003 to the parser.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query() {
                Sql { +"\u2003SELECT 1\u2003" }
            }
            """.trimIndent(),
            allWarningsAsErrors = true,
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_NAME)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    companion object {
        private const val DIAGNOSTIC_NAME = "KUERY_SQL_SYNTAX"

        // A triple-quote token to embed inside the triple-quoted source snippets below.
        private const val TRIPLE_QUOTE = "\"\"\""
    }
}
