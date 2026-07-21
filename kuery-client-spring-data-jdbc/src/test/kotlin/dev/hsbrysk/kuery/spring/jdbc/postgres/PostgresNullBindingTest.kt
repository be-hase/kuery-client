package dev.hsbrysk.kuery.spring.jdbc.postgres

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import dev.hsbrysk.kuery.spring.testing.contract.postgres.PostgresNullBindingContract
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.SqlParameterValue
import java.sql.Types

class PostgresNullBindingTest : PostgresNullBindingContract() {
    override val database get() = postgres

    private val blockingClient = PostgresTestContainer.kueryClient()

    enum class SampleEnum {
        HOGE,
    }

    @JvmInline
    value class OptionalStatus(val value: SampleEnum?)

    @Test
    fun `value class wrapping a null enum is bound as SQL NULL`() {
        // The jdbc client binds an untyped null, so no type resolution is involved; this mirrors
        // the r2dbc test, where the bindNull type must be resolved through the write pipeline.
        // given
        val username = OptionalStatus(null)
        val email = "user1@example.com"

        // when
        val count = blockingClient
            .sql { +"INSERT INTO users (username, email) VALUES ($username, $email)" }
            .rowsUpdated()

        // then
        assertThat(count).isEqualTo(1L)

        val record = blockingClient
            .sql { +"SELECT * FROM users WHERE email = $email" }
            .singleMap()
        assertThat(record["username"]).isNull()
    }

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
