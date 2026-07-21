package dev.hsbrysk.kuery.spring.jdbc.postgres

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.spring.testing.contract.postgres.PostgresNullBindingContract
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.SqlParameterValue
import java.sql.Types

class PostgresNullBindingTest : PostgresNullBindingContract() {
    override val database get() = postgres

    private val blockingClient = PostgresTestContainer.kueryClient()

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
        private val postgres = JdbcPostgresContractDatabase()
    }
}
