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

/**
 * Contract shared by the jdbc and r2dbc modules. Each module runs it via a concrete subclass
 * that supplies its `ContractDatabase`.
 *
 * The values() DSL followed by MySQL upsert clauses. Note the affected-row count contract:
 * MySQL reports 1 for an inserted row and 2 for an updated row of ON DUPLICATE KEY UPDATE.
 */
abstract class MySqlUpsertContract {
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
}
