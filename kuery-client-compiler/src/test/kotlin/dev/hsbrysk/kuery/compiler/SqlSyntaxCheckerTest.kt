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
                Sql { +"SELECT 1;" }
                Sql { +"SELECT * FROM users LIMIT ${'$'}{10}" }
                Sql {
                    +"-- users are soft-deleted"
                    +"SELECT * FROM users WHERE deleted = false"
                }
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
    fun `warn when a broken block is passed to the sql overload with a sqlId`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.KueryClient

            fun query(client: KueryClient) {
                client.sql("listUsers") { +"SELCT * FROM users" }
            }
            """.trimIndent(),
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    @Test
    fun `warn when the top-level Sql entry point is called through an import alias`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql as BuildSql

            fun query() {
                BuildSql { +"SELCT * FROM users" }
            }
            """.trimIndent(),
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    @Test
    fun `the anchor mapping survives leading blank parts removed by the final trim`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query() {
                Sql {
                    +""
                    +"WHRE id = 1"
                }
            }
            """.trimIndent(),
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        // The broken fragment is on line 6 of the compiled source.
        assertThat(result.messages).contains("Sample.kt:6")
    }

    @Test
    fun `the anchor mapping counts a part-final CR merged with the joining newline as one line break`() {
        // "\r" + the joining "\n" form a single CRLF line break in the assembled SQL, so the
        // first part spans one line, not two.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query() {
                Sql {
                    +"\r"
                    +"SELCT 1"
                }
            }
            """.trimIndent(),
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        // The broken fragment is on line 6 of the compiled source.
        assertThat(result.messages).contains("Sample.kt:6")
    }

    @Test
    fun `a nested block is checked even when the enclosing block is skipped`() {
        // The outer block contains an if statement and is skipped, but the checker fires per
        // call, so the broken nested block still warns.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query(cond: Boolean) {
                Sql {
                    +"SELECT * FROM users"
                    if (cond) {
                        val inner = Sql { +"SELCT 1" }
                    }
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
    fun `warning becomes an error with -Werror`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query() {
                Sql { +"SELCT * FROM users" }
            }
            """.trimIndent(),
            allWarningsAsErrors = true,
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    @Test
    fun `the warning stays a warning under -Werror when pinned with -Xwarning-level`() {
        // The escape hatch documented for allWarningsAsErrors users: the explicit per-diagnostic
        // level wins over -Werror, so the build succeeds while the warning is still shown.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query() {
                Sql { +"SELCT * FROM users" }
            }
            """.trimIndent(),
            allWarningsAsErrors = true,
            pluginOptions = listOf(sqlSyntaxCheckOption()),
            kotlincArguments = listOf("-Xwarning-level=$DIAGNOSTIC_NAME:warning"),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
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
    fun `no warning when the block returns early with an explicit return`() {
        // Statements after an explicit return@Sql never run, so the runtime SQL is only the
        // returned fragment. The reconstruction does not follow control flow and must skip the
        // block instead of reporting the unreachable fragment.
        // (No allWarningsAsErrors: Kotlin itself warns about the unreachable code.)
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query() {
                Sql {
                    return@Sql +"SELECT 1"
                    +"SELCT 2"
                }
            }
            """.trimIndent(),
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
    fun `warn for a subtype's own sql overload taking a SqlBuilder lambda`() {
        // Deliberate scope: entry points are matched by receiver type plus a SqlBuilder-receiver
        // lambda, so a `sql`-named overload a client subtype declares itself is checked too —
        // such a lambda builds SQL the same way.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.KueryClient
            import dev.hsbrysk.kuery.core.SqlBuilder

            class Decorated(delegate: KueryClient) : KueryClient by delegate {
                fun sql(dryRun: Boolean, block: SqlBuilder.() -> Unit) = Unit
            }

            fun query(client: Decorated) {
                client.sql(dryRun = true) { +"SELCT 1" }
            }
            """.trimIndent(),
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    @Test
    fun `warn for a subtype's sql overload even when another plain lambda argument is present`() {
        // The SQL block is the single SqlBuilder-receiver lambda among the arguments; an
        // additional plain callback must not hide it from the check.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.KueryClient
            import dev.hsbrysk.kuery.core.SqlBuilder

            class Decorated(delegate: KueryClient) : KueryClient by delegate {
                fun sql(
                    block: SqlBuilder.() -> Unit,
                    after: () -> Unit,
                ) = Unit
            }

            fun query(client: Decorated) {
                client.sql(
                    { +"SELCT 1" },
                    {},
                )
            }
            """.trimIndent(),
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(DIAGNOSTIC_NAME)
    }

    @Test
    fun `no warning when an overload takes several SqlBuilder lambdas`() {
        // With more than one SqlBuilder-receiver lambda there is no way to know which one is
        // the SQL block, so the call is skipped instead of guessing.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.KueryClient
            import dev.hsbrysk.kuery.core.SqlBuilder

            class Decorated(delegate: KueryClient) : KueryClient by delegate {
                fun sql(
                    first: SqlBuilder.() -> Unit,
                    second: SqlBuilder.() -> Unit,
                ) = Unit
            }

            fun query(client: Decorated) {
                client.sql(
                    { +"SELCT 1" },
                    { +"SELCT 2" },
                )
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
    fun `warn for sql calls through a type parameter bounded by a client interface`() {
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.KueryClient

            fun <T : KueryClient> query(client: T) {
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
    fun `warn for sql calls through an intersection-typed receiver`() {
        // A smart cast on an unbounded type parameter produces an intersection type
        // (T & KueryBlockingClient); the call must still be recognized as a client sql call.
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.KueryBlockingClient

            fun <T> query(client: T) {
                if (client is KueryBlockingClient) {
                    client.sql { +"SELCT * FROM users" }
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
    fun `no warning when an add argument is a non-trim method chain`() {
        // Only trimIndent/trimMargin chains are reconstructable; any other chain makes the text
        // statically unknown, so the block is skipped instead of guessing (the chains already
        // draw KUERY_UNSAFE_SQL_STRING).
        // when
        val result = compile(
            """
            import dev.hsbrysk.kuery.core.Sql

            fun query() {
                Sql { add("SELCT * FROM users".lowercase()) }
                Sql { add("SELCT *".plus(" FROM users")) }
            }
            """.trimIndent(),
            pluginOptions = listOf(sqlSyntaxCheckOption()),
        )

        // then
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).doesNotContain(DIAGNOSTIC_NAME)
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

    companion object {
        private const val DIAGNOSTIC_NAME = "KUERY_SQL_SYNTAX"
    }
}
