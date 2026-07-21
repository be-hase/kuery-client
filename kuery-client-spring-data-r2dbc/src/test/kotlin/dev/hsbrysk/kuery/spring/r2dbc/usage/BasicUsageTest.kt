package dev.hsbrysk.kuery.spring.r2dbc.usage

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.flow
import dev.hsbrysk.kuery.spring.r2dbc.R2dbcH2ContractDatabase
import dev.hsbrysk.kuery.spring.r2dbc.r2dbcExceptionProfile
import dev.hsbrysk.kuery.spring.testing.contract.usage.BasicUsageContract
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class BasicUsageTest : BasicUsageContract() {
    override val database get() = h2

    override val exceptionProfile get() = r2dbcExceptionProfile

    // flow/flowMap have no jdbc counterpart (KueryBlockingClient exposes sequence/sequenceMap
    // instead), so these stay r2dbc-only rather than being shared through the contract.
    @Test
    fun `flowMap returns all rows as maps`() = runTest {
        val result = kueryClient.sql { +"SELECT * FROM users ORDER BY user_id" }.flowMap().toList()
        assertThat(result).isEqualTo(
            listOf(
                mapOf(
                    "user_id" to 1,
                    "username" to "user1",
                    "email" to "user1@example.com",
                ),
                mapOf(
                    "user_id" to 2,
                    "username" to "user2",
                    "email" to "user2@example.com",
                ),
            ),
        )
    }

    @Test
    fun `flowMap returns an empty flow when no rows match`() = runTest {
        val userId = 3
        val result = kueryClient.sql { +"SELECT * FROM users WHERE user_id > $userId" }.flowMap().toList()
        assertThat(result).isEmpty()
    }

    @Test
    fun `flow returns all rows mapped to data classes`() = runTest {
        val result = kueryClient.sql { +"SELECT * FROM users ORDER BY user_id" }.flow<User>().toList()
        assertThat(result).isEqualTo(
            listOf(
                User(
                    userId = 1,
                    username = "user1",
                    email = "user1@example.com",
                ),
                User(
                    userId = 2,
                    username = "user2",
                    email = "user2@example.com",
                ),
            ),
        )
    }

    @Test
    fun `flow returns an empty flow when no rows match`() = runTest {
        val userId = 3
        val result = kueryClient.sql { +"SELECT * FROM users WHERE user_id > $userId" }.flow<User>().toList()
        assertThat(result).isEmpty()
    }

    companion object {
        private val h2 = R2dbcH2ContractDatabase()
    }
}
