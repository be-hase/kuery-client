package dev.hsbrysk.kuery.spring.r2dbc.postgres

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.spring.testing.contract.postgres.PostgresNullBindingContract
import io.r2dbc.spi.Parameters
import io.r2dbc.spi.R2dbcType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class PostgresNullBindingTest : PostgresNullBindingContract() {
    override val database get() = postgres

    @Test
    fun `bind null value via typed Parameter as an escape hatch`() = runTest {
        // given
        val username = Parameters.`in`(R2dbcType.VARCHAR)
        val email = "user1@example.com"

        // when
        val count = kueryClient
            .sql { +"INSERT INTO users (username, email) VALUES ($username, $email)" }
            .rowsUpdated()

        // then
        assertThat(count).isEqualTo(1L)
    }

    companion object {
        private val postgres = R2dbcPostgresContractDatabase()
    }
}
