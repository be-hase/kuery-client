package dev.hsbrysk.kuery.spring.r2dbc.postgres

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import dev.hsbrysk.kuery.core.values
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.BadSqlGrammarException
import org.springframework.r2dbc.core.awaitRowsUpdated
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * How PostgreSQL's parameter type inference interacts with the values() helper:
 *
 * - Binding properly typed Java objects (java.util.UUID, OffsetDateTime, ...) works, including
 *   rows that mix null and non-null values in the same column.
 * - Binding Strings for typed columns (UUID, TIMESTAMPTZ, ...) is rejected, because the driver
 *   declares string parameters as varchar and PostgreSQL does not implicitly cast them.
 *   The workaround is an explicit cast right after the placeholder (see PostgresCastBindingTest),
 *   which the values() helper cannot emit today.
 */
class PostgresValuesHelperTest {
    private val kueryClient = postgres.kueryClient()

    @BeforeEach
    fun setUp() = runTest {
        postgres.databaseClient.sql(
            """
            CREATE TABLE documents (
                id UUID PRIMARY KEY,
                parent_id UUID,
                created_at TIMESTAMPTZ NOT NULL
            )
            """.trimIndent(),
        ).fetch().awaitRowsUpdated()
    }

    @AfterEach
    fun tearDown() = runTest {
        postgres.databaseClient.sql("DROP TABLE documents").fetch().awaitRowsUpdated()
    }

    @Test
    fun `values with non-null typed rows`() = runTest {
        val createdAt = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        val input = listOf(
            listOf(UUID.randomUUID(), UUID.randomUUID(), createdAt),
            listOf(UUID.randomUUID(), UUID.randomUUID(), createdAt),
        )

        val rowsUpdated = kueryClient.sql {
            +"INSERT INTO documents (id, parent_id, created_at)"
            values(input)
        }.rowsUpdated()
        assertThat(rowsUpdated).isEqualTo(2L)
    }

    @Test
    fun `values mixing null and non-null rows on a typed column`() = runTest {
        val createdAt = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        val input = listOf(
            listOf(UUID.randomUUID(), UUID.randomUUID(), createdAt),
            listOf(UUID.randomUUID(), null, createdAt),
        )

        val rowsUpdated = kueryClient.sql {
            +"INSERT INTO documents (id, parent_id, created_at)"
            values(input)
        }.rowsUpdated()
        assertThat(rowsUpdated).isEqualTo(2L)
    }

    @Test
    fun `values binding strings for typed columns is rejected`() = runTest {
        val input = listOf(
            listOf("0f14d0ab-9605-4a62-a9e4-5ed26688389b", null, "2026-01-01 00:00:00+00"),
        )

        assertFailure {
            kueryClient.sql {
                +"INSERT INTO documents (id, parent_id, created_at)"
                values(input)
            }.rowsUpdated()
        }.isInstanceOf(BadSqlGrammarException::class)
    }

    companion object {
        private val postgres = PostgresTestContainer
    }
}
