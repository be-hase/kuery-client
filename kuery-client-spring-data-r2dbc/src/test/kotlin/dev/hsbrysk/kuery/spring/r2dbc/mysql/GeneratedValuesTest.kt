package dev.hsbrysk.kuery.spring.r2dbc.mysql

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.awaitRowsUpdated

/**
 * Pins r2dbc-mysql's generated-values behavior: the driver synthesizes a single lastInsertId row
 * (a Long) under the requested column name. The zero/multiple-rows failure paths cannot be
 * reproduced with MySQL; those are covered in the postgres package instead.
 */
class GeneratedValuesTest {
    private val kueryClient = mysql.kueryClient()

    @BeforeEach
    fun setUp() = runTest {
        mysql.databaseClient.sql(
            """
            CREATE TABLE users (
                user_id INT AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(50) NOT NULL,
                email VARCHAR(100) NOT NULL
            )
            """.trimIndent(),
        ).fetch().awaitRowsUpdated()
    }

    @AfterEach
    fun tearDown() = runTest {
        mysql.databaseClient.sql("DROP TABLE users").fetch().awaitRowsUpdated()
    }

    @Test
    fun generatedValues() = runTest {
        val username = "user1"
        val email = "user1@example.com"
        val result = kueryClient
            .sql { +"INSERT INTO users (username, email) VALUES ($username, $email)" }
            .generatedValues("user_id")
        assertThat(result).isEqualTo(mapOf("user_id" to 1L))
    }

    companion object {
        private val mysql = MySqlTestContainer
    }
}
