package dev.hsbrysk.kuery.spring.testing.contract.postgres

import assertk.assertFailure
import assertk.assertions.isInstanceOf
import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.spring.testing.ContractDatabase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DuplicateKeyException

/**
 * A unique constraint violation must surface as Spring's DuplicateKeyException so that callers
 * can catch it in their use-case layer.
 *
 * Contract shared by the jdbc and r2dbc modules. Each module runs it via a concrete subclass
 * that supplies its `ContractDatabase`.
 */
abstract class PostgresDuplicateKeyContract {
    protected abstract val database: ContractDatabase

    // lazy: `database` is not resolvable yet while this base class initializes.
    protected val kueryClient: KueryClient by lazy { database.kueryClient() }

    @BeforeEach
    fun setUpDupUsersTable() {
        database.execute(
            """
            CREATE TABLE dup_users (
                user_id INT PRIMARY KEY,
                email VARCHAR(100) NOT NULL UNIQUE
            )
            """.trimIndent(),
        )
        database.execute("INSERT INTO dup_users (user_id, email) VALUES (1, 'user1@example.com')")
    }

    @AfterEach
    fun dropDupUsersTable() {
        database.execute("DROP TABLE IF EXISTS dup_users")
    }

    @Test
    fun `unique constraint violation throws DuplicateKeyException`() = runTest {
        // given
        val email = "user1@example.com"

        // when & then
        assertFailure {
            kueryClient.sql {
                +"INSERT INTO dup_users (user_id, email) VALUES (2, $email)"
            }.rowsUpdated()
        }.isInstanceOf(DuplicateKeyException::class)
    }
}
