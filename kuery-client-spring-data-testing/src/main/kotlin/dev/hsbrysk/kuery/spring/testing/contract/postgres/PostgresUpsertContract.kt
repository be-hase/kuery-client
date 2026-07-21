package dev.hsbrysk.kuery.spring.testing.contract.postgres

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.core.values
import dev.hsbrysk.kuery.spring.testing.ContractDatabase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The values() DSL followed by PostgreSQL's ON CONFLICT DO UPDATE. Unlike MySQL, PostgreSQL
 * reports 1 affected row per row regardless of insert vs update.
 *
 * Contract shared by the jdbc and r2dbc modules. Each module runs it via a concrete subclass
 * that supplies its `ContractDatabase`.
 */
abstract class PostgresUpsertContract {
    protected abstract val database: ContractDatabase

    // lazy: `database` is not resolvable yet while this base class initializes.
    protected val kueryClient: KueryClient by lazy { database.kueryClient() }

    @BeforeEach
    fun setUpUpsertUsersTable() {
        database.execute(
            """
            CREATE TABLE upsert_users (
                user_id INT PRIMARY KEY,
                email VARCHAR(100) NOT NULL
            )
            """.trimIndent(),
        )
        database.execute("INSERT INTO upsert_users (user_id, email) VALUES (1, 'old@example.com')")
    }

    @AfterEach
    fun dropUpsertUsersTable() {
        database.execute("DROP TABLE IF EXISTS upsert_users")
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
}
