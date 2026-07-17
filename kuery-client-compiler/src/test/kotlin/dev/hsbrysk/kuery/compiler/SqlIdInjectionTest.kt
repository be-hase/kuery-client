package dev.hsbrysk.kuery.compiler

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Verifies the sqlId the compiler plugin injects into `sql(block)` calls: each compiled snippet
 * defines a top-level `fun run(): List<String>` that drives a recording client (see
 * RecordingClients.kt) and returns the observed sqlIds.
 */
@OptIn(ExperimentalCompilerApi::class)
class SqlIdInjectionTest {
    @Test
    fun `a call in a class method uses the enclosing method's FQN`() {
        // given
        val sqlIds = compileAndRun(
            """
            class UserRepository(private val client: RecordingKueryClient) {
                fun findById(id: Int) {
                    client.sql { +"SELECT * FROM users WHERE id = ${'$'}id" }
                }
            }

            fun run(): List<String> {
                val client = RecordingKueryClient()
                UserRepository(client).findById(1)
                return client.sqlIds
            }
            """.trimIndent(),
        )

        // then
        assertThat(sqlIds).containsExactly("com.example.UserRepository.findById")
    }

    @Test
    fun `a call in a suspend method uses the enclosing method's FQN`() {
        // given
        val sqlIds = compileAndRun(
            """
            import kotlinx.coroutines.runBlocking

            class UserRepository(private val client: RecordingKueryClient) {
                suspend fun findById() {
                    client.sql { +"SELECT 1" }
                }
            }

            fun run(): List<String> {
                val client = RecordingKueryClient()
                runBlocking { UserRepository(client).findById() }
                return client.sqlIds
            }
            """.trimIndent(),
        )

        // then
        assertThat(sqlIds).containsExactly("com.example.UserRepository.findById")
    }

    @Test
    fun `a call in a nested class and in a companion object includes every enclosing class name`() {
        // given
        val sqlIds = compileAndRun(
            """
            class Outer {
                class Nested {
                    fun query(client: RecordingKueryClient) {
                        client.sql { +"SELECT 1" }
                    }
                }

                companion object {
                    fun query(client: RecordingKueryClient) {
                        client.sql { +"SELECT 1" }
                    }
                }
            }

            fun run(): List<String> {
                val client = RecordingKueryClient()
                Outer.Nested().query(client)
                Outer.query(client)
                return client.sqlIds
            }
            """.trimIndent(),
        )

        // then
        assertThat(sqlIds).containsExactly(
            "com.example.Outer.Nested.query",
            "com.example.Outer.Companion.query",
        )
    }

    @Test
    fun `a call in a constructor and in an init block uses init as the method segment`() {
        // given
        val sqlIds = compileAndRun(
            """
            class Primary(client: RecordingKueryClient) {
                init {
                    client.sql { +"SELECT 1" }
                }
            }

            class Secondary {
                constructor(client: RecordingKueryClient) {
                    client.sql { +"SELECT 2" }
                }
            }

            fun run(): List<String> {
                val client = RecordingKueryClient()
                Primary(client)
                Secondary(client)
                return client.sqlIds
            }
            """.trimIndent(),
        )

        // then
        assertThat(sqlIds).containsExactly(
            "com.example.Primary.<init>",
            "com.example.Secondary.<init>",
        )
    }

    @Test
    fun `a call inside a lambda folds into the enclosing named method`() {
        // given
        val sqlIds = compileAndRun(
            """
            class UserRepository(private val client: RecordingKueryClient) {
                fun findAll() {
                    listOf(1, 2).forEach { id ->
                        client.sql { +"SELECT * FROM users WHERE id = ${'$'}id" }
                    }
                }
            }

            fun run(): List<String> {
                val client = RecordingKueryClient()
                UserRepository(client).findAll()
                return client.sqlIds
            }
            """.trimIndent(),
        )

        // then
        assertThat(sqlIds).containsExactly(
            "com.example.UserRepository.findAll",
            "com.example.UserRepository.findAll",
        )
    }

    @Test
    fun `a call in a local function appends the local function name`() {
        // given
        val sqlIds = compileAndRun(
            """
            class UserRepository(private val client: RecordingKueryClient) {
                fun findById() {
                    fun query() {
                        client.sql { +"SELECT 1" }
                    }
                    query()
                }
            }

            fun run(): List<String> {
                val client = RecordingKueryClient()
                UserRepository(client).findById()
                return client.sqlIds
            }
            """.trimIndent(),
        )

        // then
        assertThat(sqlIds).containsExactly("com.example.UserRepository.findById.query")
    }

    @Test
    fun `a call in a top-level function uses the Kotlin FQN without the file facade`() {
        // given
        val sqlIds = compileAndRun(
            """
            fun topLevelQuery(client: RecordingKueryClient) {
                client.sql { +"SELECT 1" }
            }

            fun run(): List<String> {
                val client = RecordingKueryClient()
                topLevelQuery(client)
                return client.sqlIds
            }
            """.trimIndent(),
        )

        // then
        assertThat(sqlIds).containsExactly("com.example.topLevelQuery")
    }

    @Test
    fun `a call in a property initializer and in a getter uses the property name`() {
        // given
        val sqlIds = compileAndRun(
            """
            class UserRepository(private val client: RecordingKueryClient) {
                val initialized = client.sql { +"SELECT 1" }

                val computed get() = client.sql { +"SELECT 2" }
            }

            fun run(): List<String> {
                val client = RecordingKueryClient()
                UserRepository(client).computed
                return client.sqlIds
            }
            """.trimIndent(),
        )

        // then
        assertThat(sqlIds).containsExactly(
            "com.example.UserRepository.initialized",
            "com.example.UserRepository.computed",
        )
    }

    @Test
    fun `a call in an inline function uses the inline function's own FQN at every call site`() {
        // given
        val sqlIds = compileAndRun(
            """
            inline fun inlineQuery(client: RecordingKueryClient) {
                client.sql { +"SELECT 1" }
            }

            fun run(): List<String> {
                val client = RecordingKueryClient()
                inlineQuery(client)
                return client.sqlIds
            }
            """.trimIndent(),
        )

        // then
        assertThat(sqlIds).containsExactly("com.example.inlineQuery")
    }

    @Test
    fun `multiple calls in one method are disambiguated with a numbered suffix in source order`() {
        // given
        val sqlIds = compileAndRun(
            """
            class UserRepository(private val client: RecordingKueryClient) {
                fun findUserAndDetail() {
                    client.sql { +"SELECT * FROM users" }
                    client.sql { +"SELECT * FROM user_details" }
                }
            }

            fun run(): List<String> {
                val client = RecordingKueryClient()
                UserRepository(client).findUserAndDetail()
                return client.sqlIds
            }
            """.trimIndent(),
        )

        // then
        assertThat(sqlIds).containsExactly(
            "com.example.UserRepository.findUserAndDetail#1",
            "com.example.UserRepository.findUserAndDetail#2",
        )
    }

    @Test
    fun `calls in lambdas of the same method share the numbering of that method`() {
        // given
        val sqlIds = compileAndRun(
            """
            class UserRepository(private val client: RecordingKueryClient) {
                fun findAll() {
                    listOf(1).map { client.sql { +"SELECT 1" } }
                    client.sql { +"SELECT 2" }
                }
            }

            fun run(): List<String> {
                val client = RecordingKueryClient()
                UserRepository(client).findAll()
                return client.sqlIds
            }
            """.trimIndent(),
        )

        // then
        assertThat(sqlIds).containsExactly(
            "com.example.UserRepository.findAll#1",
            "com.example.UserRepository.findAll#2",
        )
    }

    @Test
    fun `a method with a single call keeps the plain id even when other methods contain calls`() {
        // given
        val sqlIds = compileAndRun(
            """
            class UserRepository(private val client: RecordingKueryClient) {
                fun findUser() {
                    client.sql { +"SELECT * FROM users" }
                }

                fun findDetail() {
                    client.sql { +"SELECT * FROM user_details" }
                }
            }

            fun run(): List<String> {
                val client = RecordingKueryClient()
                UserRepository(client).findUser()
                UserRepository(client).findDetail()
                return client.sqlIds
            }
            """.trimIndent(),
        )

        // then
        assertThat(sqlIds).containsExactly(
            "com.example.UserRepository.findUser",
            "com.example.UserRepository.findDetail",
        )
    }

    @Test
    fun `an explicit sqlId is kept as-is and does not participate in the numbering`() {
        // given
        val sqlIds = compileAndRun(
            """
            class UserRepository(private val client: RecordingKueryClient) {
                fun find() {
                    client.sql("my-explicit-id") { +"SELECT * FROM users" }
                    client.sql { +"SELECT * FROM user_details" }
                }
            }

            fun run(): List<String> {
                val client = RecordingKueryClient()
                UserRepository(client).find()
                return client.sqlIds
            }
            """.trimIndent(),
        )

        // then
        assertThat(sqlIds).containsExactly(
            "my-explicit-id",
            "com.example.UserRepository.find",
        )
    }

    @Test
    fun `a call through a subtype interface of KueryClient is rewritten`() {
        // given
        val sqlIds = compileAndRun(
            """
            import dev.hsbrysk.kuery.core.KueryClient

            interface MyClient : KueryClient

            class MyClientImpl(delegate: KueryClient) : MyClient, KueryClient by delegate

            fun run(): List<String> {
                val recording = RecordingKueryClient()
                val client: MyClient = MyClientImpl(recording)
                client.sql { +"SELECT 1" }
                return recording.sqlIds
            }
            """.trimIndent(),
        )

        // then
        assertThat(sqlIds).containsExactly("com.example.run")
    }

    @Test
    fun `a call on KueryBlockingClient is rewritten the same way`() {
        // given
        val sqlIds = compileAndRun(
            """
            class UserRepository(private val client: RecordingKueryBlockingClient) {
                fun findById(id: Int) {
                    client.sql { +"SELECT * FROM users WHERE id = ${'$'}id" }
                }
            }

            fun run(): List<String> {
                val client = RecordingKueryBlockingClient()
                UserRepository(client).findById(1)
                return client.sqlIds
            }
            """.trimIndent(),
        )

        // then
        assertThat(sqlIds).containsExactly("com.example.UserRepository.findById")
    }

    private fun compileAndRun(body: String): List<String> {
        val source = """
            package com.example

            import com.example.compiler.RecordingKueryBlockingClient
            import com.example.compiler.RecordingKueryClient

        """.trimIndent() + "\n" + body

        val result = compile(source)
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)

        val runner = result.classLoader.loadClass("com.example.SampleKt")

        @Suppress("UNCHECKED_CAST")
        return runner.getMethod("run").invoke(null) as List<String>
    }
}
