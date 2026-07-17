package dev.hsbrysk.kuery.spring.r2dbc.mysql

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.awaitRowsUpdated
import java.time.LocalDateTime

/**
 * Keyset (cursor) pagination via a row-value comparison with two interpolated values:
 * `(created_at, id) > ($cursorCreatedAt, $cursorId)`.
 */
class MySqlKeysetPaginationTest {
    private val kueryClient = mysql.kueryClient()

    @BeforeEach
    fun setUp() = runTest {
        mysql.databaseClient.sql(
            """
            CREATE TABLE events (
                id INT PRIMARY KEY,
                created_at DATETIME NOT NULL
            )
            """.trimIndent(),
        ).fetch().awaitRowsUpdated()
        mysql.databaseClient.sql(
            """
            INSERT INTO events (id, created_at) VALUES
            (1, '2026-01-01 10:00:00'),
            (2, '2026-01-01 10:00:00'),
            (3, '2026-01-01 10:05:00'),
            (4, '2026-01-01 10:05:00'),
            (5, '2026-01-01 10:10:00')
            """.trimIndent(),
        ).fetch().awaitRowsUpdated()
    }

    @AfterEach
    fun tearDown() = runTest {
        mysql.databaseClient.sql("DROP TABLE events").fetch().awaitRowsUpdated()
    }

    @Test
    fun `keyset pagination ascending`() = runTest {
        // given
        val cursorCreatedAt = LocalDateTime.of(2026, 1, 1, 10, 0, 0)
        val cursorId = 1

        // when
        val page = kueryClient.sql {
            +"SELECT id FROM events"
            +"WHERE (created_at, id) > ($cursorCreatedAt, $cursorId)"
            +"ORDER BY created_at, id"
            +"LIMIT 2"
        }.listMap()

        // then
        assertThat(page.map { it["id"] }).isEqualTo(listOf(2, 3))
    }

    @Test
    fun `keyset pagination descending`() = runTest {
        // given
        val cursorCreatedAt = LocalDateTime.of(2026, 1, 1, 10, 5, 0)
        val cursorId = 4

        // when
        val page = kueryClient.sql {
            +"SELECT id FROM events"
            +"WHERE (created_at, id) < ($cursorCreatedAt, $cursorId)"
            +"ORDER BY created_at DESC, id DESC"
            +"LIMIT 2"
        }.listMap()

        // then
        assertThat(page.map { it["id"] }).isEqualTo(listOf(3, 2))
    }

    companion object {
        private val mysql = MySqlTestContainer
    }
}
