package dev.hsbrysk.kuery.compiler

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class SqlSyntaxCheckerTest {
    @Test
    fun `warn when the SQL of a block does not parse`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query() {
                Sql { +"SELCT * FROM users" }
            }
            """.trimIndent(),
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    @Test
    fun `warn when a statement assembled from multiple adds does not parse`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int) {
                Sql {
                    +"SELECT *"
                    +"FORM users"
                    +"WHERE id = ${'$'}id"
                }
            }
            """.trimIndent(),
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    @Test
    fun `no warning for valid statically-known SQL forms`() {
        // allWarningsAsErrors = true, so any unexpected warning fails the compilation as well
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            const val SELECT_ALL = "SELECT * FROM users"

            fun query(id: Int, name: String) {
                Sql { +"SELECT 1" }
                Sql { add("SELECT * FROM users WHERE id = ${'$'}id AND name = ${'$'}name") }
                Sql {
                    +"SELECT *"
                    +"FROM users"
                    +"WHERE id = ${'$'}id"
                }
                Sql { add("SELECT *\nFROM users\nWHERE id = ${'$'}id".trimIndent()) }
                Sql { add(SELECT_ALL) }
                Sql { +"INSERT INTO users (id, name) VALUES (${'$'}id, ${'$'}name)" }
                Sql { +"UPDATE users SET name = ${'$'}name WHERE id = ${'$'}id" }
            }

            // Common MySQL / PostgreSQL flavored syntax must not draw false positives.
            fun dialects(id: Int, name: String) {
                Sql { +"SELECT `order` FROM `users` WHERE id = ${'$'}id" }
                Sql { +"INSERT INTO users (id, name) VALUES (${'$'}id, ${'$'}name) ON DUPLICATE KEY UPDATE name = ${'$'}name" }
                Sql { +"INSERT INTO users (id, name) VALUES (${'$'}id, ${'$'}name) ON CONFLICT (id) DO UPDATE SET name = ${'$'}name" }
                Sql { +"SELECT id::text FROM users" }
                Sql { +"SELECT * FROM users WHERE name LIKE ${'$'}name LIMIT ${'$'}id OFFSET ${'$'}id" }
                Sql { +"SELECT * FROM users WHERE id = ${'$'}id FOR UPDATE" }
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
    fun `warn when a KueryBlockingClient sql block does not parse`() {
        // The blocking client mirrors KueryClient and must be recognized as an entry point too.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.KueryBlockingClient

            fun query(client: KueryBlockingClient) {
                client.sql { +"SELCT * FROM users" }
            }
            """.trimIndent(),
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

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
    fun `no warning when an add argument is an if expression`() {
        // An if/when result is compile-time-safe but not reconstructable, so the block is skipped
        // even when a branch is broken.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(flag: Boolean) {
                Sql { add(if (flag) "SELCT 1" else "SELECT 2") }
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
    fun `no warning for an empty sql block`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query() {
                Sql { }
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
    fun `no warning for a like-named function that is not a client sql call`() {
        // The entry-point pre-filter matches on the callee name first; a user's own function
        // named Sql must not be treated as a builder block.
        // when
        val result = compile(
            """
            fun Sql(block: () -> Unit) {}

            fun query() {
                Sql { }
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
    fun `no warning when the block contains statements other than add or unaryPlus`() {
        // The full statement is not statically known, so even a broken fragment is skipped.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(name: String?) {
                Sql {
                    +"SELCT * FROM users"
                    name?.let { +"WHERE name = ${'$'}it" }
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
    fun `no warning when the sql string is not statically known`() {
        // The variable already draws KUERY_UNSAFE_SQL_STRING; the syntax check must stay silent.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(sql: String) {
                Sql { add(sql) }
            }
            """.trimIndent(),
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_NAME)
    }

    @Test
    fun `no warning when the option is not enabled`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query() {
                Sql { +"SELCT * FROM users" }
            }
            """.trimIndent(),
            allWarningsAsErrors = true,
        )

        // then
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_NAME)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `warning can be suppressed by diagnostic name`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            @Suppress("$DIAGNOSTIC_NAME")
            fun query() {
                Sql { +"SELCT * FROM users" }
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
    fun `the warning is anchored to the add whose line fails to parse`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int) {
                Sql {
                    +"SELECT *"
                    +"FROM users"
                    +"WHRE id = ${'$'}id"
                }
            }
            """.trimIndent(),
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        // The broken fragment is on line 7 of the compiled source.
        assertThat(result.messages).contains("Sample.kt:7")
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
    fun `no warning when the block adds to a different builder`() {
        // `other.add(...)` contributes to another statement at runtime, so this block's SQL is
        // not statically known and must be skipped.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql
            import dev.hsbrysk.kuery.core.SqlBuilder

            fun query(other: SqlBuilder) {
                Sql {
                    other.add("WHERE deleted = false")
                    +"SELECT * FROM users"
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
    fun `warn when the block is passed as a named argument`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.KueryClient

            fun query(client: KueryClient) {
                client.sql(block = { +"SELCT * FROM users" }, sqlId = "listUsers")
            }
            """.trimIndent(),
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    @Test
    fun `warn for sql calls through a subtype of the client interfaces`() {
        // Both a sub-interface (fake override) and a delegating class (real override) must be
        // recognized as entry points.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.KueryClient
            import dev.hsbrysk.kuery.core.Sql

            interface MyClient : KueryClient

            class Decorated(delegate: KueryClient) : KueryClient by delegate

            fun query(myClient: MyClient, decorated: Decorated) {
                myClient.sql { +"SELCT * FROM users" }
                decorated.sql { +"SELCT * FROM users" }
            }
            """.trimIndent(),
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(Regex(DIAGNOSTIC_NAME).findAll(result.messages).count()).isEqualTo(2)
    }

    @Test
    fun `the warning message contains the parser reason without exception class names`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query() {
                Sql { +"SELCT * FROM users" }
            }
            """.trimIndent(),
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.messages).contains("Encountered unexpected token")
        assertThat(result.messages).doesNotContain("net.sf.jsqlparser")
    }

    @Test
    fun `warn when the SQL uses a feature the configured dialect does not support`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int, name: String) {
                Sql { +"INSERT INTO users (id, name) VALUES (${'$'}id, ${'$'}name) ON DUPLICATE KEY UPDATE name = ${'$'}name" }
            }
            """.trimIndent(),
            pluginOptions = listOf(sqlSyntaxCheckOption(), sqlSyntaxCheckDialectOption("postgresql")),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIALECT_DIAGNOSTIC_NAME)
    }

    @Test
    fun `no warning when the SQL matches the configured dialect`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(id: Int, name: String) {
                Sql { +"SELECT `order` FROM `users` WHERE id = ${'$'}id FOR UPDATE" }
                Sql { +"INSERT INTO users (id, name) VALUES (${'$'}id, ${'$'}name) ON DUPLICATE KEY UPDATE name = ${'$'}name" }
                Sql { +"SELECT * FROM users WHERE name LIKE ${'$'}name LIMIT ${'$'}id OFFSET ${'$'}id" }
            }
            """.trimIndent(),
            allWarningsAsErrors = true,
            pluginOptions = listOf(sqlSyntaxCheckOption(), sqlSyntaxCheckDialectOption("mysql")),
        )

        // then
        assertThat(result.messages).doesNotContain(DIALECT_DIAGNOSTIC_NAME)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `a syntax error rather than a dialect violation is reported when the SQL does not parse`() {
        // A statement that fails to parse cannot be feature-checked, so only the syntax warning
        // fires even with a dialect configured.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query() {
                Sql { +"SELCT * FROM users" }
            }
            """.trimIndent(),
            pluginOptions = listOf(sqlSyntaxCheckOption(), sqlSyntaxCheckDialectOption("postgresql")),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
        assertThat(result.messages).doesNotContain(DIALECT_DIAGNOSTIC_NAME)
    }

    @Test
    fun `the dialect option alone enables the syntax check`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query() {
                Sql { +"SELCT * FROM users" }
            }
            """.trimIndent(),
            pluginOptions = listOf(sqlSyntaxCheckDialectOption("mysql")),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    @Test
    fun `dialect warning can be suppressed by diagnostic name`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            @Suppress("$DIALECT_DIAGNOSTIC_NAME")
            fun query(id: Int, name: String) {
                Sql { +"INSERT INTO users (id, name) VALUES (${'$'}id, ${'$'}name) ON DUPLICATE KEY UPDATE name = ${'$'}name" }
            }
            """.trimIndent(),
            allWarningsAsErrors = true,
            pluginOptions = listOf(sqlSyntaxCheckOption(), sqlSyntaxCheckDialectOption("postgresql")),
        )

        // then
        assertThat(result.messages).doesNotContain(DIALECT_DIAGNOSTIC_NAME)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    companion object {
        private const val DIAGNOSTIC_NAME = "KUERY_SQL_SYNTAX"
        private const val DIALECT_DIAGNOSTIC_NAME = "KUERY_SQL_DIALECT"

        // A triple-quote token to embed inside the triple-quoted source snippets below.
        private const val TRIPLE_QUOTE = "\"\"\""
    }
}
