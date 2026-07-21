package dev.hsbrysk.kuery.spring.testing.contract.postgres

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.cause
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.messageContains
import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.core.values
import dev.hsbrysk.kuery.spring.testing.ContractDatabase
import dev.hsbrysk.kuery.spring.testing.ExceptionProfile
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
 *   The workaround is an explicit cast right after the placeholder (see
 *   PostgresCastBindingContract), which the values() helper cannot emit today.
 *
 * Contract shared by the jdbc and r2dbc modules. Each module runs it via a concrete subclass
 * that supplies its `ContractDatabase`.
 */
abstract class PostgresValuesHelperContract {
    protected abstract val database: ContractDatabase

    protected abstract val exceptionProfile: ExceptionProfile

    // lazy: `database` is not resolvable yet while this base class initializes.
    protected val kueryClient: KueryClient by lazy { database.kueryClient() }

    @BeforeEach
    fun setUpDocumentsTable() {
        database.execute(
            """
            CREATE TABLE documents (
                id UUID PRIMARY KEY,
                parent_id UUID,
                created_at TIMESTAMPTZ NOT NULL
            )
            """.trimIndent(),
        )
    }

    @AfterEach
    fun dropDocumentsTable() {
        database.execute("DROP TABLE IF EXISTS documents")
    }

    @Test
    fun `values with non-null typed rows`() = runTest {
        // given
        val createdAt = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        val input = listOf(
            listOf(UUID.randomUUID(), UUID.randomUUID(), createdAt),
            listOf(UUID.randomUUID(), UUID.randomUUID(), createdAt),
        )

        // when
        val rowsUpdated = kueryClient.sql {
            +"INSERT INTO documents (id, parent_id, created_at)"
            values(input)
        }.rowsUpdated()

        // then
        assertThat(rowsUpdated).isEqualTo(2L)
    }

    @Test
    fun `values mixing null and non-null rows on a typed column`() = runTest {
        // given
        val createdAt = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        val input = listOf(
            listOf(UUID.randomUUID(), UUID.randomUUID(), createdAt),
            listOf(UUID.randomUUID(), null, createdAt),
        )

        // when
        val rowsUpdated = kueryClient.sql {
            +"INSERT INTO documents (id, parent_id, created_at)"
            values(input)
        }.rowsUpdated()

        // then
        assertThat(rowsUpdated).isEqualTo(2L)
    }

    @Test
    fun `values mixing conflicting parameter types in one column is rejected`() = runTest {
        // PostgreSQL unifies the parameter types of a VALUES column across rows. A null is sent
        // without type information and simply adopts the column type (see the test above), but
        // a single String-typed row poisons the whole multi-row VALUES even when every other
        // row is properly typed: the column's common type resolves to varchar and then fails
        // against the uuid target column. (Historically this surfaced as "VALUES types
        // character varying and uuid cannot be matched" on older driver/server combinations.)

        // given
        val createdAt = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        val input = listOf(
            listOf(UUID.randomUUID(), UUID.randomUUID(), createdAt),
            listOf(UUID.randomUUID(), "0f14d0ab-9605-4a62-a9e4-5ed26688389b", createdAt),
        )

        // when & then
        assertFailure {
            kueryClient.sql {
                +"INSERT INTO documents (id, parent_id, created_at)"
                values(input)
            }.rowsUpdated()
        }.isInstanceOf(exceptionProfile.badSqlGrammarException)
            .cause().isNotNull()
            .messageContains("is of type uuid but expression is of type character varying")
    }

    @Test
    fun `values binding strings for typed columns is rejected`() = runTest {
        // given
        val input = listOf(
            listOf("0f14d0ab-9605-4a62-a9e4-5ed26688389b", null, "2026-01-01 00:00:00+00"),
        )

        // when & then
        assertFailure {
            kueryClient.sql {
                +"INSERT INTO documents (id, parent_id, created_at)"
                values(input)
            }.rowsUpdated()
        }.isInstanceOf(exceptionProfile.badSqlGrammarException)
    }
}
