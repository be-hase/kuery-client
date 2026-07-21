package dev.hsbrysk.kuery.spring.r2dbc.postgres

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import dev.hsbrysk.kuery.spring.testing.contract.postgres.PostgresNullBindingContract
import io.r2dbc.spi.Parameters
import io.r2dbc.spi.R2dbcType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class PostgresNullBindingTest : PostgresNullBindingContract() {
    override val database get() = postgres

    enum class SampleEnum {
        HOGE,
    }

    @JvmInline
    value class OptionalStatus(val value: SampleEnum?)

    @Test
    fun `value class wrapping a null enum is bound as SQL NULL`() = runTest {
        // The bindNull type must be resolved through the write pipeline (enum -> String here),
        // not the raw underlying type, which the driver has no codec for.
        // given
        val username = OptionalStatus(null)
        val email = "user1@example.com"

        // when
        val count = kueryClient
            .sql { +"INSERT INTO users (username, email) VALUES ($username, $email)" }
            .rowsUpdated()

        // then
        assertThat(count).isEqualTo(1L)

        val record = kueryClient
            .sql { +"SELECT * FROM users WHERE email = $email" }
            .singleMap()
        assertThat(record["username"]).isNull()
    }

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
