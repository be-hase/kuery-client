package dev.hsbrysk.kuery.spring.r2dbc.postgres

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import io.r2dbc.spi.Parameters
import io.r2dbc.spi.R2dbcType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.awaitRowsUpdated

class PostgresNullBindingTest {
    private val kueryClient = postgres.kueryClient()

    @BeforeEach
    fun setUp() = runTest {
        postgres.databaseClient.sql(
            """
            CREATE TABLE users (
                user_id SERIAL PRIMARY KEY,
                username VARCHAR(50),
                email VARCHAR(100) NOT NULL,
                age INT
            )
            """.trimIndent(),
        ).fetch().awaitRowsUpdated()
    }

    @AfterEach
    fun tearDown() = runTest {
        postgres.databaseClient.sql("DROP TABLE users").fetch().awaitRowsUpdated()
    }

    @Test
    fun `bind non-null value`() = runTest {
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
    fun `bind null value`() = runTest {
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
    fun `bind null value to an int column`() = runTest {
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
    fun `compare with null value in WHERE clause`() = runTest {
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
    fun `select null value directly`() = runTest {
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
        private val postgres = PostgresTestContainer
    }
}
