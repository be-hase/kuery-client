package dev.hsbrysk.kuery.spring.testing.contract.mysql

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.core.values
import dev.hsbrysk.kuery.spring.testing.ContractDatabase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * Contract shared by the jdbc and r2dbc modules. Each module runs it via a concrete subclass
 * that supplies its `ContractDatabase`.
 *
 * Contrast to PostgresValuesHelperTest: MySQL does not infer VALUES parameter types per row, so
 * mixing null and non-null rows on a typed column just works.
 */
abstract class MySqlValuesHelperContract {
    protected abstract val database: ContractDatabase

    // lazy: `database` is not resolvable yet while this base class initializes.
    protected val kueryClient: KueryClient by lazy { database.kueryClient() }

    @BeforeEach
    fun setUpDocumentsTable() {
        database.execute(
            """
            CREATE TABLE documents (
                id INT PRIMARY KEY,
                parent_id BINARY(16),
                created_at DATETIME
            )
            """.trimIndent(),
        )
    }

    @AfterEach
    fun dropDocumentsTable() {
        database.execute("DROP TABLE IF EXISTS documents")
    }

    @Test
    fun `values mixing null and non-null rows on typed columns`() = runTest {
        // given
        val createdAt = LocalDateTime.of(2026, 1, 1, 0, 0, 0)
        val input = listOf(
            listOf(1, ByteArray(16) { it.toByte() }, createdAt),
            listOf(2, null, null),
        )

        // when
        val rowsUpdated = kueryClient.sql {
            +"INSERT INTO documents (id, parent_id, created_at)"
            values(input)
        }.rowsUpdated()

        // then
        assertThat(rowsUpdated).isEqualTo(2L)
    }
}
