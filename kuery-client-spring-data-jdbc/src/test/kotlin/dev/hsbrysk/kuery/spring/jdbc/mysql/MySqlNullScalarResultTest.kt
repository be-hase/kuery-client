package dev.hsbrysk.kuery.spring.jdbc.mysql

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.spring.testing.contract.mysql.MySqlNullScalarResultContract
import org.junit.jupiter.api.Test

class MySqlNullScalarResultTest : MySqlNullScalarResultContract() {
    override val database get() = mysql

    private val blockingClient = MySqlTestContainer.kueryClient()

    @Test
    fun `sequence preserves NULL elements`() {
        // given
        insert("user1@example.com", age = 20)
        insert("user2@example.com", age = null)

        // when
        val ages: List<Int?> = blockingClient.sql { +"SELECT age FROM users ORDER BY user_id" }
            .sequence(Int::class)
            .toList()

        // then
        assertThat(ages).isEqualTo(listOf(20, null))
    }

    private fun insert(
        email: String,
        age: Int?,
    ) {
        blockingClient.sql { +"INSERT INTO users (email, age) VALUES ($email, $age)" }.rowsUpdated()
    }

    companion object {
        private val mysql = JdbcMySqlContractDatabase()
    }
}
