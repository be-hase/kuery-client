package dev.hsbrysk.kuery.spring.r2dbc.postgres

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.dao.IncorrectResultSizeDataAccessException
import org.springframework.r2dbc.core.awaitRowsUpdated

/**
 * Pins the failure contract of [dev.hsbrysk.kuery.core.KueryClient.FetchSpec.generatedValues]:
 * EmptyResultDataAccessException when the driver emits no generated-value rows,
 * IncorrectResultSizeDataAccessException when it emits more than one.
 *
 * These cases are exercised with PostgreSQL because its RETURNING-based generated values actually
 * emit zero or multiple rows. r2dbc-mysql synthesizes a single lastInsertId row even for UPDATE or
 * multi-row INSERT, so neither failure path can be reproduced with MySQL.
 */
class PostgresGeneratedValuesTest {
    private val kueryClient = postgres.kueryClient()

    @BeforeEach
    fun setUp() = runTest {
        postgres.databaseClient.sql(
            """
            CREATE TABLE users (
                user_id SERIAL PRIMARY KEY,
                username VARCHAR(50) NOT NULL,
                email VARCHAR(100) NOT NULL
            )
            """.trimIndent(),
        ).fetch().awaitRowsUpdated()
    }

    @AfterEach
    fun tearDown() = runTest {
        postgres.databaseClient.sql("DROP TABLE users").fetch().awaitRowsUpdated()
    }

    @Test
    fun `generatedValues returns the generated key of a single insert`() = runTest {
        // when
        val result = kueryClient
            .sql { +"INSERT INTO users (username, email) VALUES ('user1', 'user1@example.com')" }
            .generatedValues("user_id")

        // then
        assertThat(result).isEqualTo(mapOf("user_id" to 1))
    }

    @Test
    fun `generatedValues throws EmptyResultDataAccessException when no keys are generated`() = runTest {
        assertFailure {
            kueryClient
                .sql { +"UPDATE users SET username = 'updated' WHERE user_id = 1" }
                .generatedValues("user_id")
        }.isInstanceOf(EmptyResultDataAccessException::class)
    }

    @Test
    fun `generatedValues throws IncorrectResultSizeDataAccessException for multiple keys`() = runTest {
        assertFailure {
            kueryClient
                .sql {
                    +"""
                    INSERT INTO users (username, email) VALUES
                    ('user1', 'user1@example.com'),
                    ('user2', 'user2@example.com')
                    """.trimIndent()
                }
                .generatedValues("user_id")
        }.isInstanceOf(IncorrectResultSizeDataAccessException::class)
    }

    companion object {
        private val postgres = PostgresTestContainer
    }
}
