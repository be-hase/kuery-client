package dev.hsbrysk.kuery.spring.jdbc.postgres

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
class PostgresDuplicateKeyTest {
    private val kueryClient = postgres.kueryClient()

    @BeforeEach
    fun setUp() {
        postgres.jdbcClient.sql(
            """
            CREATE TABLE dup_users (
                user_id INT PRIMARY KEY,
                email VARCHAR(100) NOT NULL UNIQUE
            )
            """.trimIndent(),
        ).update()
        postgres.jdbcClient.sql("INSERT INTO dup_users (user_id, email) VALUES (1, 'user1@example.com')").update()
    }

    @AfterEach
    fun tearDown() {
        postgres.jdbcClient.sql("DROP TABLE dup_users").update()
    }

    @Test
    fun `unique constraint violation throws DuplicateKeyException`() {
        val email = "user1@example.com"
        assertFailure {
            kueryClient.sql {
                +"INSERT INTO dup_users (user_id, email) VALUES (2, $email)"
            }.rowsUpdated()
        }.isInstanceOf(DuplicateKeyException::class)
    }

    companion object {
        private val postgres = PostgresTestContainer
    }
}
