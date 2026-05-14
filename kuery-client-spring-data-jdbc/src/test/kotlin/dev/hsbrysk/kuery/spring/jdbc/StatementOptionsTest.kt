package dev.hsbrysk.kuery.spring.jdbc

import assertk.assertThat
import assertk.assertions.hasSize
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StatementOptionsTest {
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
        repeat(10) { i ->
            mysql.jdbcClient.sql("INSERT INTO users (username, email) VALUES ('user$i', 'user$i@example.com')").update()
        }
    }

    @AfterEach
    fun tearDown() {
        mysql.jdbcClient.sql("DROP TABLE users").update()
    }

    @Test
    fun `fetchSize does not break results`() {
        val result = kueryClient.sql { +"SELECT * FROM users" }
            .fetchSize(3)
            .listMap()
        assertThat(result).hasSize(10)
    }

    @Test
    fun `maxRows limits result size`() {
        val result = kueryClient.sql { +"SELECT * FROM users" }
            .maxRows(5)
            .listMap()
        assertThat(result).hasSize(5)
    }

    @Test
    fun `queryTimeoutSeconds does not break normal query`() {
        val result = kueryClient.sql { +"SELECT * FROM users" }
            .queryTimeoutSeconds(10)
            .listMap()
        assertThat(result).hasSize(10)
    }

    @Test
    fun `chain of options works correctly`() {
        val result = kueryClient.sql { +"SELECT * FROM users" }
            .fetchSize(3)
            .maxRows(4)
            .queryTimeoutSeconds(10)
            .listMap()
        assertThat(result).hasSize(4)
    }

    @Test
    fun `options are applied immutably`() {
        val base = kueryClient.sql { +"SELECT * FROM users" }
        val limited = base.maxRows(3)

        val baseResult = base.listMap()
        val limitedResult = limited.listMap()

        assertThat(baseResult).hasSize(10)
        assertThat(limitedResult).hasSize(3)
    }

    companion object {
        private val mysql = MySqlTestContainer()

        @AfterAll
        @JvmStatic
        fun afterAll() {
            mysql.close()
        }
    }
}
