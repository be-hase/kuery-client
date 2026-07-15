package dev.hsbrysk.kuery.spring.jdbc.mysql

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.EmptyResultDataAccessException
import java.math.BigInteger

/**
 * Pins MySQL Connector/J's generated-keys behavior: the auto-increment value is reported under the
 * driver-specific "GENERATED_KEY" label (as a BigInteger), regardless of the requested column names.
 */
class GeneratedValuesTest {
    private val kueryClient = mysql.kueryClient()

    @BeforeEach
    fun setUp() {
        mysql.jdbcClient.sql(
            """
            CREATE TABLE users (
                user_id INT AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(50) NOT NULL,
                email VARCHAR(100) NOT NULL
            )
            """.trimIndent(),
        ).update()
    }

    @AfterEach
    fun tearDown() {
        mysql.jdbcClient.sql("DROP TABLE users").update()
    }

    @Test
    fun generatedValues() {
        val username = "user1"
        val email = "user1@example.com"
        val result = kueryClient
            .sql { +"INSERT INTO users (username, email) VALUES ($username, $email)" }
            .generatedValues("user_id")
        assertThat(result).isEqualTo(mapOf("GENERATED_KEY" to BigInteger.valueOf(1)))
    }

    @Test
    fun `generatedValues no generated keys`() {
        // Unlike H2 (which reports the affected row's identity even for UPDATE), MySQL emits no
        // generated keys here, exercising the EmptyResultDataAccessException failure path.
        assertFailure {
            kueryClient
                .sql { +"UPDATE users SET username = 'updated' WHERE user_id = 1" }
                .generatedValues()
        }.isInstanceOf(EmptyResultDataAccessException::class)
    }

    companion object {
        private val mysql = MySqlTestContainer
    }
}
