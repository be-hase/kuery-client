package dev.hsbrysk.kuery.spring.r2dbc

import assertk.assertThat
import assertk.assertions.hasSize
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.awaitRowsUpdated

class StatementOptionsTest {
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
        repeat(10) { i ->
            mysql.databaseClient.sql("INSERT INTO users (username, email) VALUES ('user$i', 'user$i@example.com')")
                .fetch().awaitRowsUpdated()
        }
    }

    @AfterEach
    fun tearDown() = runTest {
        mysql.databaseClient.sql("DROP TABLE users").fetch().awaitRowsUpdated()
    }

    @Test
    fun `fetchSize does not break results`() = runTest {
        val result = kueryClient.sql { +"SELECT * FROM users" }
            .fetchSize(3)
            .listMap()
        assertThat(result).hasSize(10)
    }

    @Test
    fun `options are applied immutably`() = runTest {
        val base = kueryClient.sql { +"SELECT * FROM users" }
        val withFetchSize = base.fetchSize(3)

        val baseResult = base.listMap()
        val withFetchSizeResult = withFetchSize.listMap()

        assertThat(baseResult).hasSize(10)
        assertThat(withFetchSizeResult).hasSize(10)
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
