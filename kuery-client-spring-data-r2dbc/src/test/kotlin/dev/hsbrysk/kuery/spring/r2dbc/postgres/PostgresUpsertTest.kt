package dev.hsbrysk.kuery.spring.r2dbc.postgres

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.values
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.awaitRowsUpdated

/**
 * The values() DSL followed by PostgreSQL's ON CONFLICT DO UPDATE. Unlike MySQL, PostgreSQL
 * reports 1 affected row per row regardless of insert vs update.
 */
class PostgresUpsertTest {
    private val kueryClient = postgres.kueryClient()

    @BeforeEach
    fun setUp() = runTest {
        postgres.databaseClient.sql(
            """
            CREATE TABLE upsert_users (
                user_id INT PRIMARY KEY,
                email VARCHAR(100) NOT NULL
            )
            """.trimIndent(),
        ).fetch().awaitRowsUpdated()
        postgres.databaseClient.sql("INSERT INTO upsert_users (user_id, email) VALUES (1, 'old@example.com')")
            .fetch().awaitRowsUpdated()
    }

    @AfterEach
    fun tearDown() = runTest {
        postgres.databaseClient.sql("DROP TABLE upsert_users").fetch().awaitRowsUpdated()
    }

    @Test
    fun `values followed by ON CONFLICT DO UPDATE`() = runTest {
        // given
        val input = listOf(
            listOf(1, "new1@example.com"),
            listOf(2, "new2@example.com"),
        )

        // when
        val rowsUpdated = kueryClient.sql {
            +"INSERT INTO upsert_users (user_id, email)"
            values(input)
            +"ON CONFLICT (user_id) DO UPDATE SET email = EXCLUDED.email"
        }.rowsUpdated()

        // then
        assertThat(rowsUpdated).isEqualTo(2L)

        val result = kueryClient
            .sql { +"SELECT user_id, email FROM upsert_users ORDER BY user_id" }
            .listMap()
        assertThat(result.map { it["email"] }).isEqualTo(listOf("new1@example.com", "new2@example.com"))
    }

    companion object {
        private val postgres = PostgresTestContainer
    }
}
