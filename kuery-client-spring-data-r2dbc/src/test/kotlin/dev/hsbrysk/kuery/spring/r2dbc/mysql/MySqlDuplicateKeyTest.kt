package dev.hsbrysk.kuery.spring.r2dbc.mysql

import assertk.assertFailure
import assertk.assertions.isInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DuplicateKeyException
import org.springframework.r2dbc.core.awaitRowsUpdated

/**
 * A unique constraint violation must surface as Spring's DuplicateKeyException so that callers
 * can catch it in their use-case layer.
 */
class MySqlDuplicateKeyTest {
    private val kueryClient = mysql.kueryClient()

    @BeforeEach
    fun setUp() = runTest {
        mysql.databaseClient.sql(
            """
            CREATE TABLE dup_users (
                user_id INT PRIMARY KEY,
                email VARCHAR(100) NOT NULL,
                UNIQUE KEY uq_email (email)
            )
            """.trimIndent(),
        ).fetch().awaitRowsUpdated()
        mysql.databaseClient.sql("INSERT INTO dup_users (user_id, email) VALUES (1, 'user1@example.com')")
            .fetch().awaitRowsUpdated()
    }

    @AfterEach
    fun tearDown() = runTest {
        mysql.databaseClient.sql("DROP TABLE dup_users").fetch().awaitRowsUpdated()
    }

    @Test
    fun `unique constraint violation throws DuplicateKeyException`() = runTest {
        val email = "user1@example.com"
        assertFailure {
            kueryClient.sql {
                +"INSERT INTO dup_users (user_id, email) VALUES (2, $email)"
            }.rowsUpdated()
        }.isInstanceOf(DuplicateKeyException::class)
    }

    companion object {
        private val mysql = MySqlTestContainer
    }
}
