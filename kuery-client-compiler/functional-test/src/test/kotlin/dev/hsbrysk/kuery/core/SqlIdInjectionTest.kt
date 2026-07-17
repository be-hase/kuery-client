package dev.hsbrysk.kuery.core

import assertk.assertThat
import assertk.assertions.containsExactly
import dev.hsbrysk.kuery.core.internal.SqlIds.id
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

/**
 * Verifies the sqlId the compiler plugin injects into the sqlId-less `sql(block)` overloads:
 * the FQN of the call site's enclosing declaration, disambiguated with a `#N` suffix when one
 * declaration contains several calls. Only literally written blocks (lambdas and function
 * references) are rewritten; anything else — variables, mocking-library matchers — stays
 * untouched. The call-site shapes live in the fixtures at the bottom of this file, so all ids
 * start with this file's package.
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

    @Test
    fun `a function reference passed as the block gets the call site's id`() {
        // when
        queryByFunctionReference(client)

        // then
        assertThat(client.sqlIds).containsExactly("dev.hsbrysk.kuery.core.queryByFunctionReference")
    }

    @Test
    fun `a block passed as a variable is not rewritten and resolves to NONE`() {
        // given
        val block: SqlBuilder.() -> Unit = { +"SELECT * FROM users" }

        // when
        client.sql(block)

        // then
        assertThat(client.sqlIds).containsExactly("NONE")
    }

    @Test
    fun `overloads with a single call each keep their plain ids`() {
        // when
        OverloadedRepository(client).find(1)
        OverloadedRepository(client).find("user1")

        // then
        assertThat(client.sqlIds).containsExactly(
            "dev.hsbrysk.kuery.core.OverloadedRepository.find",
            "dev.hsbrysk.kuery.core.OverloadedRepository.find",
        )
    }

    @Test
    fun `mockk argument matchers on the sqlId-less overload keep working`() {
        // given: any() must reach mockk unwrapped, otherwise the matcher binding breaks
        val mock = mockk<KueryBlockingClient>()
        every { mock.sql(any()) } returns mockk()

        // when
        mock.sql { +"SELECT * FROM users" }

        // then
        verify(exactly = 1) { mock.sql(any()) }
    }

    @Test
    fun `the wrapped block still builds the same Sql as the raw block`() {
        // when
        UserRepository(client).findById(1)

        // then
        assertThat(client.sqls).containsExactly(
            Sql("SELECT * FROM users WHERE user_id = :p0", listOf(NamedSqlParameter("p0", 1))),
        )
    }

    @Test
    fun `a function reference that needs adaptation is not rewritten and resolves to NONE`() {
        // when: the referenced function declares a default argument, so the reference is
        // represented as an adapter rather than a plain function reference
        queryByAdaptedReference(client)

        // then: no id, but the adapter still runs with the default argument applied
        assertThat(client.sqlIds).containsExactly("NONE")
        assertThat(client.sqls).containsExactly(
            Sql("SELECT * FROM users LIMIT :p0", listOf(NamedSqlParameter("p0", 10))),
        )
    }

    @Test
    fun `a non-literal call in a numbered method does not consume a number`() {
        // given
        val block: SqlBuilder.() -> Unit = { +"SELECT * FROM user_details" }

        // when
        UserRepository(client).findMixed(block)

        // then
        assertThat(client.sqlIds).containsExactly(
            "dev.hsbrysk.kuery.core.UserRepository.findMixed#1",
            "NONE",
            "dev.hsbrysk.kuery.core.UserRepository.findMixed#2",
        )
    }

    @Test
    fun `a call through a hand-written decorator keeps the call site's id`() {
        // when
        queryThroughDecorator(ForwardingClientDecorator(client))

        // then
        assertThat(client.sqlIds).containsExactly("dev.hsbrysk.kuery.core.queryThroughDecorator")
    }

    @Test
    fun `a call in an extension on a generic receiver bounded by the client is rewritten`() {
        // when
        client.helperQuery()

        // then
        assertThat(client.sqlIds).containsExactly("dev.hsbrysk.kuery.core.helperQuery")
    }

    @Test
    fun `a call in an object declaration includes the object name`() {
        // when
        ObjectRepository.query(client)

        // then
        assertThat(client.sqlIds).containsExactly("dev.hsbrysk.kuery.core.ObjectRepository.query")
    }

    @Test
    fun `a call in an interface default method uses the interface's FQN`() {
        // when
        DefaultMethodRepository(client).queryWithDefault()

        // then
        assertThat(client.sqlIds).containsExactly(
            "dev.hsbrysk.kuery.core.RepositoryDefaults.queryWithDefault",
        )
    }

    @Test
    fun `a call in an enum entry body includes the entry name`() {
        // when
        QueryKind.ALL.query(client)

        // then
        assertThat(client.sqlIds).containsExactly("dev.hsbrysk.kuery.core.QueryKind.ALL.query")
    }
}

/**
 * Records the sqlId of every `sql` call, and the [Sql] the block builds — wrapping must never
 * change what the block does. The terminal operations are irrelevant here, so the returned
 * FetchSpec is a bare mockk stub.
 */
@OptIn(KueryClientInternalApi::class)
private class RecordingClient : KueryBlockingClient {
    val sqlIds = mutableListOf<String>()
    val sqls = mutableListOf<Sql>()

    override fun sql(
        sqlId: String,
        block: SqlBuilder.() -> Unit,
    ): KueryBlockingClient.FetchSpec {
        sqlIds += sqlId
        sqls += Sql(block)
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

    fun findMixed(block: SqlBuilder.() -> Unit) {
        client.sql { +"SELECT * FROM users" }
        client.sql(block)
        client.sql { +"SELECT COUNT(*) FROM users" }
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
// not re-wrap the block; the caller's id has to survive the pass-through. The bridge forwards
// its parameter — not a literal — so the literal-only rewrite rule keeps it untouched.
private class SubtypeClient(delegate: KueryBlockingClient) :
    SubtypeBlockingClient,
    KueryBlockingClient by delegate

private fun queryThroughSubtype(client: SubtypeBlockingClient) {
    client.sql { +"SELECT * FROM users" }
}

private fun selectAllUsers(builder: SqlBuilder) {
    builder.add("SELECT * FROM users")
}

private fun queryByFunctionReference(client: KueryBlockingClient) {
    client.sql(::selectAllUsers)
}

private class OverloadedRepository(private val client: KueryBlockingClient) {
    fun find(id: Int) {
        client.sql { +"SELECT * FROM users WHERE user_id = $id" }
    }

    fun find(name: String) {
        client.sql { +"SELECT * FROM users WHERE username = $name" }
    }
}

private fun selectAllUsersWithLimit(
    builder: SqlBuilder,
    limit: Int = 10,
) {
    builder.add("SELECT * FROM users LIMIT $limit")
}

private fun queryByAdaptedReference(client: KueryBlockingClient) {
    client.sql(::selectAllUsersWithLimit)
}

// A user-written pass-through, as opposed to the compiler-generated bridge in SubtypeClient:
// its forwarding call passes a parameter — not a literal — so it is never re-wrapped.
private class ForwardingClientDecorator(private val delegate: KueryBlockingClient) : KueryBlockingClient {
    override fun sql(
        sqlId: String,
        block: SqlBuilder.() -> Unit,
    ): KueryBlockingClient.FetchSpec = delegate.sql(sqlId, block)

    override fun sql(block: SqlBuilder.() -> Unit): KueryBlockingClient.FetchSpec = delegate.sql(block)
}

private fun queryThroughDecorator(client: KueryBlockingClient) {
    client.sql { +"SELECT * FROM users" }
}

// The receiver's static type is a type parameter, which has no class of its own; the call must
// still be identified through its resolved declaration.
private fun <T : KueryBlockingClient> T.helperQuery() {
    sql { +"SELECT * FROM users" }
}

private object ObjectRepository {
    fun query(client: KueryBlockingClient) {
        client.sql { +"SELECT * FROM users" }
    }
}

private interface RepositoryDefaults {
    val client: KueryBlockingClient

    fun queryWithDefault() {
        client.sql { +"SELECT * FROM users" }
    }
}

private class DefaultMethodRepository(override val client: KueryBlockingClient) : RepositoryDefaults

private enum class QueryKind {
    ALL {
        override fun query(client: KueryBlockingClient) {
            client.sql { +"SELECT * FROM users" }
        }
    },
    ;

    abstract fun query(client: KueryBlockingClient)
}
