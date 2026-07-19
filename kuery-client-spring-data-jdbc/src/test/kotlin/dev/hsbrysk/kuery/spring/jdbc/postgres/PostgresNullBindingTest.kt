package dev.hsbrysk.kuery.spring.jdbc.postgres

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.SqlParameterValue
import java.sql.Types

class PostgresNullBindingTest {
    private val kueryClient = postgres.kueryClient()

    enum class SampleEnum {
        HOGE,
    }

    @JvmInline
    value class OptionalStatus(val value: SampleEnum?)

    @BeforeEach
    fun setUp() {
        postgres.jdbcClient.sql(
            """
            CREATE TABLE users (
                user_id SERIAL PRIMARY KEY,
                username VARCHAR(50),
                email VARCHAR(100) NOT NULL,
                age INT
            )
            """.trimIndent(),
        ).update()
    }

    @AfterEach
    fun tearDown() {
        postgres.jdbcClient.sql("DROP TABLE users").update()
    }

    @Test
    fun `bind non-null value`() {
        // given
        val username = "user1"
        val email = "user1@example.com"

        // when
        val count = kueryClient
            .sql { +"INSERT INTO users (username, email) VALUES ($username, $email)" }
            .rowsUpdated()

        // then
        assertThat(count).isEqualTo(1L)
    }

    @Test
    fun `bind null value`() {
        // given
        val username: String? = null
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
    fun `bind null value to an int column`() {
        // given
        val age: Int? = null
        val email = "user1@example.com"

        // when
        val count = kueryClient
            .sql { +"INSERT INTO users (email, age) VALUES ($email, $age)" }
            .rowsUpdated()

        // then
        assertThat(count).isEqualTo(1L)
    }

    @Test
    fun `bind value class wrapping a null enum`() {
        // The jdbc client binds an untyped null, so no type resolution is involved; this mirrors
        // the r2dbc test, where the bindNull type must be resolved through the write pipeline.
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
    fun `compare with null value in WHERE clause`() {
        // given
        val username: String? = null

        // when
        val list = kueryClient
            .sql { +"SELECT * FROM users WHERE username = $username" }
            .listMap()

        // then
        assertThat(list).isEqualTo(emptyList())
    }

    @Test
    fun `select null value directly`() {
        // given
        val value: String? = null

        // when
        val record = kueryClient
            .sql { +"SELECT $value AS v" }
            .singleMap()

        // then
        assertThat(record["v"]).isNull()
    }

    @Test
    fun `bind null value via typed SqlParameterValue as an escape hatch`() {
        // given
        val username = SqlParameterValue(Types.VARCHAR, null)
        val email = "user1@example.com"

        // when
        val count = kueryClient
            .sql { +"INSERT INTO users (username, email) VALUES ($username, $email)" }
            .rowsUpdated()

        // then
        assertThat(count).isEqualTo(1L)
    }

    companion object {
        private val postgres = PostgresTestContainer
    }
}
