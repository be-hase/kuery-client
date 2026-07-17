package dev.hsbrysk.kuery.core

import assertk.assertThat
import assertk.assertions.containsExactly
import dev.hsbrysk.kuery.core.internal.SqlIds.id
import io.mockk.mockk
import org.junit.jupiter.api.Test

/**
 * Verifies with the real compiler plugin applied (and `-Xverify-ir=error` active) that the
 * sqlId-less `sql(block)` overload receives a compile-time id derived from the enclosing
 * declaration. Exhaustive coverage of the id derivation lives in the compiler module's
 * SqlIdInjectionTest; this covers the representative shapes end to end.
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
    fun `an explicit sqlId is kept as-is`() {
        // when
        UserRepository(client).findWithExplicitId()

        // then
        assertThat(client.sqlIds).containsExactly("my-explicit-id")
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

private class UserRepository(private val client: KueryBlockingClient) {
    fun findById(id: Int) {
        client.sql { +"SELECT * FROM users WHERE user_id = $id" }
    }

    fun findEach(ids: List<Int>) {
        ids.forEach { id ->
            client.sql { +"SELECT * FROM users WHERE user_id = $id" }
        }
    }

    fun findUserAndDetail(id: Int) {
        client.sql { +"SELECT * FROM users WHERE user_id = $id" }
        client.sql { +"SELECT * FROM user_details WHERE user_id = $id" }
    }

    fun findWithExplicitId() {
        client.sql("my-explicit-id") { +"SELECT * FROM users" }
    }
}
