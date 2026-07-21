package dev.hsbrysk.kuery.spring.jdbc.mysql

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.spring.testing.contract.mysql.MySqlNullBindingContract
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.SqlParameterValue
import java.sql.Types

class MySqlNullBindingTest : MySqlNullBindingContract() {
    override val database get() = mysql

    private val blockingClient = MySqlTestContainer.kueryClient()

    @Test
    fun `bind null value via typed SqlParameterValue as an escape hatch`() {
        // given
        val username = SqlParameterValue(Types.VARCHAR, null)
        val email = "user1@example.com"

        // when
        val count = blockingClient
            .sql { +"INSERT INTO users (username, email) VALUES ($username, $email)" }
            .rowsUpdated()

        // then
        assertThat(count).isEqualTo(1L)
    }

    companion object {
        private val mysql = JdbcMySqlContractDatabase()
    }
}
