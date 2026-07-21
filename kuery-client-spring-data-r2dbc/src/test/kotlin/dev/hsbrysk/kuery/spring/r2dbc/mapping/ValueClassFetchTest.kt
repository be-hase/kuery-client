package dev.hsbrysk.kuery.spring.r2dbc.mapping

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.flow
import dev.hsbrysk.kuery.spring.r2dbc.R2dbcH2ContractDatabase
import dev.hsbrysk.kuery.spring.r2dbc.r2dbcExceptionProfile
import dev.hsbrysk.kuery.spring.testing.contract.mapping.ValueClassFetchContract
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ValueClassFetchTest : ValueClassFetchContract() {
    override val database get() = h2

    override val exceptionProfile get() = r2dbcExceptionProfile

    // flow has no jdbc counterpart (KueryBlockingClient exposes sequence instead), so these stay
    // r2dbc-only rather than being shared through the contract.
    @Test
    fun `flow keeps a SQL NULL value class scalar as a null element`() = runTest {
        // The scalar mapper maps a SQL NULL row to a null element; flow() must preserve it
        // (non-null underlying type), like list().
        // given
        database.execute("INSERT INTO converter (text) VALUES ('user1'), (NULL)")

        // when & then
        val result: List<UserName?> = kueryClient.sql {
            +"SELECT text FROM converter ORDER BY id"
        }.flow<UserName>().toList()
        assertThat(result).isEqualTo(listOf(UserName("user1"), null))
    }

    @Test
    fun `flow maps a nullable-underlying value class scalar SQL NULL to an inner null`() = runTest {
        // given
        database.execute("INSERT INTO converter (text) VALUES ('user1'), (NULL)")

        // when & then
        val result: List<OptionalUserName> = kueryClient.sql {
            +"SELECT text FROM converter ORDER BY id"
        }.flow<OptionalUserName>().toList()
        assertThat(result).isEqualTo(listOf(OptionalUserName("user1"), OptionalUserName(null)))
    }

    companion object {
        private val h2 = R2dbcH2ContractDatabase()
    }
}
