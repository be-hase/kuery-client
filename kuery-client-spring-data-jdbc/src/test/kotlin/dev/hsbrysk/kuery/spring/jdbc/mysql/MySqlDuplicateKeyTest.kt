package dev.hsbrysk.kuery.spring.jdbc.mysql

import assertk.assertFailure
import assertk.assertions.isInstanceOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DuplicateKeyException

/**
 * A unique constraint violation must surface as Spring's DuplicateKeyException so that callers
 * can catch it in their use-case layer.
 */
class MySqlDuplicateKeyTest {
    private val kueryClient = mysql.kueryClient()

    @BeforeEach
    fun setUp() {
        mysql.jdbcClient.sql(
            """
            CREATE TABLE dup_users (
                user_id INT PRIMARY KEY,
                email VARCHAR(100) NOT NULL,
                UNIQUE KEY uq_email (email)
            )
            """.trimIndent(),
        ).update()
        mysql.jdbcClient.sql("INSERT INTO dup_users (user_id, email) VALUES (1, 'user1@example.com')").update()
    }

    @AfterEach
    fun tearDown() {
        mysql.jdbcClient.sql("DROP TABLE dup_users").update()
    }

    @Test
    fun `unique constraint violation throws DuplicateKeyException`() {
        // given
        val email = "user1@example.com"

        // when & then
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
