package dev.hsbrysk.kuery.spring.jdbc.usage

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import dev.hsbrysk.kuery.core.sequence
import dev.hsbrysk.kuery.spring.jdbc.JdbcH2ContractDatabase
import dev.hsbrysk.kuery.spring.jdbc.jdbcExceptionProfile
import dev.hsbrysk.kuery.spring.testing.contract.usage.BasicUsageContract
import org.junit.jupiter.api.Test
import org.springframework.dao.IncorrectResultSizeDataAccessException

class BasicUsageTest : BasicUsageContract() {
    override val database get() = h2

    override val exceptionProfile get() = jdbcExceptionProfile

    private val blockingClient = h2.blockingClient()

    @Test
    fun `sequenceMap returns all rows as maps`() {
        val result = blockingClient.sql { +"SELECT * FROM users ORDER BY user_id" }.sequenceMap().toList()
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
    fun `sequenceMap returns an empty sequence when no rows match`() {
        val userId = 3
        val result = blockingClient.sql { +"SELECT * FROM users WHERE user_id > $userId" }.sequenceMap().toList()
        assertThat(result).isEmpty()
    }

    @Test
    fun `sequence returns all rows mapped to data classes`() {
        val result: List<User> = blockingClient.sql { +"SELECT * FROM users ORDER BY user_id" }
            .sequence<User>()
            .toList()
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
    fun `sequence returns an empty sequence when no rows match`() {
        val userId = 3
        val result: List<User> = blockingClient.sql { +"SELECT * FROM users WHERE user_id > $userId" }
            .sequence<User>()
            .toList()
        assertThat(result).isEmpty()
    }

    @Test
    fun `generatedValues throws when multiple rows generate keys`() {
        assertFailure {
            blockingClient
                .sql {
                    +"""
                    INSERT INTO users (username, email) VALUES
                    ('user3', 'user3@example.com'),
                    ('user4', 'user4@example.com')
                    """.trimIndent()
                }
                .generatedValues("user_id")
        }.isInstanceOf(IncorrectResultSizeDataAccessException::class)
    }

    companion object {
        private val h2 = JdbcH2ContractDatabase()
    }
}
