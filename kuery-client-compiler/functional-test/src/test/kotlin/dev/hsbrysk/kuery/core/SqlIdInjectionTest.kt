package dev.hsbrysk.kuery.core

import assertk.assertThat
import assertk.assertions.containsExactly
import dev.hsbrysk.kuery.core.internal.SqlIds.id
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

/**
 * Verifies the sqlId the compiler plugin injects into the sqlId-less `sql(block)` overloads:
 * the FQN of the call site's enclosing declaration, disambiguated with a `#N` suffix when one
 * declaration contains several calls. The call-site shapes live in the fixtures at the bottom
 * of this file, so all ids start with this file's package.
 */
class SqlIdInjectionTest {
    private val client = RecordingClient()

    @Test
    fun `a call in a class method uses the enclosing method's FQN`() {
        // when
        UserRepository(client).findById(1)

        // then
        assertThat(client.sqlIds).containsExactly("dev.hsbrysk.kuery.core.UserRepository.findById")
    }

    @Test
    fun `a call in a suspend method on the coroutine client is rewritten the same way`() {
        // given
        val coroutineClient = RecordingCoroutineClient()

        // when
        runBlocking { CoroutineUserRepository(coroutineClient).findById(1) }

        // then
        assertThat(coroutineClient.sqlIds).containsExactly("dev.hsbrysk.kuery.core.CoroutineUserRepository.findById")
    }

    @Test
    fun `a call in a nested class and in a companion object includes every enclosing class name`() {
        // when
        Outer.Nested().query(client)
        Outer.query(client)

        // then
        assertThat(client.sqlIds).containsExactly(
            "dev.hsbrysk.kuery.core.Outer.Nested.query",
            "dev.hsbrysk.kuery.core.Outer.Companion.query",
        )
    }

    @Test
    fun `a call in an init block and in a constructor uses init as the method segment`() {
        // when
        InitializingRepository(client)
        SecondaryConstructorRepository(client)

        // then
        assertThat(client.sqlIds).containsExactly(
            "dev.hsbrysk.kuery.core.InitializingRepository.<init>",
            "dev.hsbrysk.kuery.core.SecondaryConstructorRepository.<init>",
        )
    }

    @Test
    fun `a call inside a lambda folds into the enclosing named method`() {
        // when
        UserRepository(client).findEach(listOf(1, 2))

        // then
        assertThat(client.sqlIds).containsExactly(
            "dev.hsbrysk.kuery.core.UserRepository.findEach",
            "dev.hsbrysk.kuery.core.UserRepository.findEach",
        )
    }

    @Test
    fun `a call in a local function appends the local function name`() {
        // when
        UserRepository(client).findWithLocalFunction()

        // then
        assertThat(client.sqlIds).containsExactly(
            "dev.hsbrysk.kuery.core.UserRepository.findWithLocalFunction.query",
        )
    }

    @Test
    fun `a call in a top-level function uses the Kotlin FQN without the file facade`() {
        // when
        topLevelQuery(client)

        // then
        assertThat(client.sqlIds).containsExactly("dev.hsbrysk.kuery.core.topLevelQuery")
    }

    @Test
    fun `a call in a property initializer and in a getter uses the property name`() {
        // when
        PropertyRepository(client).computed

        // then
        assertThat(client.sqlIds).containsExactly(
            "dev.hsbrysk.kuery.core.PropertyRepository.initialized",
            "dev.hsbrysk.kuery.core.PropertyRepository.computed",
        )
    }

    @Test
    fun `a call in an inline function uses the inline function's own FQN at every call site`() {
        // when
        inlineQuery(client) { }

        // then
        assertThat(client.sqlIds).containsExactly("dev.hsbrysk.kuery.core.inlineQuery")
    }

    @Test
    fun `multiple calls in one method are disambiguated with a numbered suffix in source order`() {
        // when
        UserRepository(client).findUserAndDetail(1)

        // then
        assertThat(client.sqlIds).containsExactly(
            "dev.hsbrysk.kuery.core.UserRepository.findUserAndDetail#1",
            "dev.hsbrysk.kuery.core.UserRepository.findUserAndDetail#2",
        )
    }

    @Test
    fun `calls in lambdas of the same method share the numbering of that method`() {
        // when
        UserRepository(client).findAllAndCount()

        // then
        assertThat(client.sqlIds).containsExactly(
            "dev.hsbrysk.kuery.core.UserRepository.findAllAndCount#1",
            "dev.hsbrysk.kuery.core.UserRepository.findAllAndCount#2",
        )
    }

    @Test
    fun `a method with a single call keeps the plain id even when other methods contain calls`() {
        // when
        UserRepository(client).findUser()
        UserRepository(client).findDetail()

        // then
        assertThat(client.sqlIds).containsExactly(
            "dev.hsbrysk.kuery.core.UserRepository.findUser",
            "dev.hsbrysk.kuery.core.UserRepository.findDetail",
        )
    }

    @Test
    fun `an explicit sqlId is kept as-is and does not participate in the numbering`() {
        // when
        UserRepository(client).findWithExplicitId()

        // then
        assertThat(client.sqlIds).containsExactly(
            "my-explicit-id",
            "dev.hsbrysk.kuery.core.UserRepository.findWithExplicitId",
        )
    }

    @Test
    fun `a call through a subtype interface keeps the call site's id across the delegation bridge`() {
        // when
        queryThroughSubtype(SubtypeClient(client))

        // then
        assertThat(client.sqlIds).containsExactly("dev.hsbrysk.kuery.core.queryThroughSubtype")
    }
}

/**
 * Records the sqlId of every `sql` call. The terminal operations are irrelevant here, so the
 * returned FetchSpec is a bare mockk stub.
 */
@OptIn(KueryClientInternalApi::class)
private class RecordingClient : KueryBlockingClient {
    val sqlIds = mutableListOf<String>()

    override fun sql(
        sqlId: String,
        block: SqlBuilder.() -> Unit,
    ): KueryBlockingClient.FetchSpec {
        sqlIds += sqlId
        return mockk()
    }

    override fun sql(block: SqlBuilder.() -> Unit): KueryBlockingClient.FetchSpec = sql(block.id(), block)
}

/**
 * The [KueryClient] counterpart of [RecordingClient].
 */
@OptIn(KueryClientInternalApi::class)
private class RecordingCoroutineClient : KueryClient {
    val sqlIds = mutableListOf<String>()

    override fun sql(
        sqlId: String,
        block: SqlBuilder.() -> Unit,
    ): KueryClient.FetchSpec {
        sqlIds += sqlId
        return mockk()
    }

    override fun sql(block: SqlBuilder.() -> Unit): KueryClient.FetchSpec = sql(block.id(), block)
}

private class UserRepository(private val client: KueryBlockingClient) {
    fun findById(id: Int) {
        client.sql { +"SELECT * FROM users WHERE user_id = $id" }
    }

    fun findEach(ids: List<Int>) {
        ids.forEach { id ->
            client.sql { +"SELECT * FROM users WHERE user_id = $id" }
        }
    }

    fun findWithLocalFunction() {
        fun query() {
            client.sql { +"SELECT * FROM users" }
        }
        query()
    }

    fun findUserAndDetail(id: Int) {
        client.sql { +"SELECT * FROM users WHERE user_id = $id" }
        client.sql { +"SELECT * FROM user_details WHERE user_id = $id" }
    }

    fun findAllAndCount() {
        listOf(1).map { id -> client.sql { +"SELECT * FROM users WHERE user_id = $id" } }
        client.sql { +"SELECT COUNT(*) FROM users" }
    }

    fun findUser() {
        client.sql { +"SELECT * FROM users" }
    }

    fun findDetail() {
        client.sql { +"SELECT * FROM user_details" }
    }

    fun findWithExplicitId() {
        client.sql("my-explicit-id") { +"SELECT * FROM users" }
        client.sql { +"SELECT * FROM user_details" }
    }
}

private class CoroutineUserRepository(private val client: KueryClient) {
    suspend fun findById(id: Int) {
        client.sql { +"SELECT * FROM users WHERE user_id = $id" }
    }
}

private class Outer {
    class Nested {
        fun query(client: KueryBlockingClient) {
            client.sql { +"SELECT * FROM users" }
        }
    }

    companion object {
        fun query(client: KueryBlockingClient) {
            client.sql { +"SELECT * FROM users" }
        }
    }
}

private class InitializingRepository(client: KueryBlockingClient) {
    init {
        client.sql { +"SELECT * FROM users" }
    }
}

private class SecondaryConstructorRepository {
    constructor(client: KueryBlockingClient) {
        client.sql { +"SELECT * FROM users" }
    }
}

private class PropertyRepository(private val client: KueryBlockingClient) {
    val initialized = client.sql { +"SELECT * FROM users" }

    val computed get() = client.sql { +"SELECT * FROM user_details" }
}

private fun topLevelQuery(client: KueryBlockingClient) {
    client.sql { +"SELECT * FROM users" }
}

private inline fun inlineQuery(
    client: KueryBlockingClient,
    action: () -> Unit,
) {
    client.sql { +"SELECT * FROM users" }
    action()
}

private interface SubtypeBlockingClient : KueryBlockingClient

// The compiler-generated delegation bridge `override fun sql(block) = delegate.sql(block)` must
// not re-wrap the block; the caller's id has to survive the pass-through.
private class SubtypeClient(delegate: KueryBlockingClient) :
    SubtypeBlockingClient,
    KueryBlockingClient by delegate

private fun queryThroughSubtype(client: SubtypeBlockingClient) {
    client.sql { +"SELECT * FROM users" }
}
