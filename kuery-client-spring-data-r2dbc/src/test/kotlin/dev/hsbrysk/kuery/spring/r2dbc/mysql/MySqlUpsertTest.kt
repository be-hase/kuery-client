package dev.hsbrysk.kuery.spring.r2dbc.mysql

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.values
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.awaitRowsUpdated

/**
 * The values() DSL followed by MySQL upsert clauses. Note the affected-row count contract:
 * MySQL reports 1 for an inserted row and 2 for an updated row of ON DUPLICATE KEY UPDATE.
 */
class MySqlUpsertTest {
    private val kueryClient = mysql.kueryClient()

    @BeforeEach
    fun setUp() = runTest {
        mysql.databaseClient.sql(
            """
            CREATE TABLE upsert_users (
                user_id INT PRIMARY KEY,
                email VARCHAR(100) NOT NULL
            )
            """.trimIndent(),
        ).fetch().awaitRowsUpdated()
        mysql.databaseClient.sql("INSERT INTO upsert_users (user_id, email) VALUES (1, 'old@example.com')")
            .fetch().awaitRowsUpdated()
    }

    @AfterEach
    fun tearDown() = runTest {
        mysql.databaseClient.sql("DROP TABLE upsert_users").fetch().awaitRowsUpdated()
    }

    @Test
    fun `values followed by ON DUPLICATE KEY UPDATE with row alias`() = runTest {
        // given
        val input = listOf(
            listOf(1, "new1@example.com"),
            listOf(2, "new2@example.com"),
        )

        // when
        val rowsUpdated = kueryClient.sql {
            +"INSERT INTO upsert_users (user_id, email)"
            values(input)
            +"AS new ON DUPLICATE KEY UPDATE email = new.email"
        }.rowsUpdated()

        // then
        // 2 for the updated row (user_id=1) + 1 for the inserted row (user_id=2)
        assertThat(rowsUpdated).isEqualTo(3L)

        assertUpserted()
    }

    @Test
    fun `values followed by ON DUPLICATE KEY UPDATE with VALUES function`() = runTest {
        // MySQL's VALUES(col) function (deprecated in 8.0.20 but widely used) shares its name
        // with the values() DSL; they must not interfere.

        // given
        val input = listOf(
            listOf(1, "new1@example.com"),
            listOf(2, "new2@example.com"),
        )

        // when
        val rowsUpdated = kueryClient.sql {
            +"INSERT INTO upsert_users (user_id, email)"
            values(input)
            +"ON DUPLICATE KEY UPDATE email = VALUES(email)"
        }.rowsUpdated()

        // then
        assertThat(rowsUpdated).isEqualTo(3L)

        assertUpserted()
    }

    private suspend fun assertUpserted() {
        val result = kueryClient
            .sql { +"SELECT user_id, email FROM upsert_users ORDER BY user_id" }
            .listMap()
        assertThat(result.map { it["email"] }).isEqualTo(listOf("new1@example.com", "new2@example.com"))
    }

    companion object {
        private val mysql = MySqlTestContainer
    }
}
