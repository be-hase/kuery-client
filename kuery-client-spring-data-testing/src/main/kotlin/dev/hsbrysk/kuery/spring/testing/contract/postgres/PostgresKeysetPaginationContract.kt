package dev.hsbrysk.kuery.spring.testing.contract.postgres

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.spring.testing.ContractDatabase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * Keyset (cursor) pagination via a row-value comparison with two interpolated values:
 * `(created_at, id) > ($cursorCreatedAt, $cursorId)`.
 *
 * Contract shared by the jdbc and r2dbc modules. Each module runs it via a concrete subclass
 * that supplies its `ContractDatabase`.
 */
abstract class PostgresKeysetPaginationContract {
    protected abstract val database: ContractDatabase

    // lazy: `database` is not resolvable yet while this base class initializes.
    protected val kueryClient: KueryClient by lazy { database.kueryClient() }

    @BeforeEach
    fun setUpEventsTable() {
        database.execute(
            """
            CREATE TABLE events (
                id INT PRIMARY KEY,
                created_at TIMESTAMP NOT NULL
            )
            """.trimIndent(),
        )
        database.execute(
            """
            INSERT INTO events (id, created_at) VALUES
            (1, '2026-01-01 10:00:00'),
            (2, '2026-01-01 10:00:00'),
            (3, '2026-01-01 10:05:00'),
            (4, '2026-01-01 10:05:00'),
            (5, '2026-01-01 10:10:00')
            """.trimIndent(),
        )
    }

    @AfterEach
    fun dropEventsTable() {
        database.execute("DROP TABLE IF EXISTS events")
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
}
