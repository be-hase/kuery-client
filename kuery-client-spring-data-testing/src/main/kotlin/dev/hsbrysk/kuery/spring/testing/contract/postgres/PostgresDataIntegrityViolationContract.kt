package dev.hsbrysk.kuery.spring.testing.contract.postgres

import assertk.assertFailure
import assertk.assertions.isInstanceOf
import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.spring.testing.ContractDatabase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException

/**
 * Contract shared by the jdbc and r2dbc modules. Each module runs it via a concrete subclass
 * that supplies its `ContractDatabase`.
 *
 * Integrity constraint violations other than duplicate keys (covered by
 * [PostgresDuplicateKeyContract]) must surface as Spring's DataIntegrityViolationException so
 * that callers can catch them in their use-case layer.
 */
abstract class PostgresDataIntegrityViolationContract {
    protected abstract val database: ContractDatabase

    // lazy: `database` is not resolvable yet while this base class initializes.
    protected val kueryClient: KueryClient by lazy { database.kueryClient() }

    @BeforeEach
    fun setUpIntegrityTables() {
        database.execute(
            """
            CREATE TABLE integrity_parents (
                parent_id INT PRIMARY KEY
            )
            """.trimIndent(),
        )
        database.execute(
            """
            CREATE TABLE integrity_children (
                child_id INT PRIMARY KEY,
                parent_id INT NOT NULL,
                CONSTRAINT fk_integrity_children_parent
                    FOREIGN KEY (parent_id) REFERENCES integrity_parents (parent_id)
            )
            """.trimIndent(),
        )
    }

    @AfterEach
    fun dropIntegrityTables() {
        database.execute("DROP TABLE IF EXISTS integrity_children")
        database.execute("DROP TABLE IF EXISTS integrity_parents")
    }

    @Test
    fun `foreign key constraint violation throws DataIntegrityViolationException`() = runTest {
        // given
        val nonExistentParentId = 999

        // when & then
        assertFailure {
            kueryClient.sql {
                +"INSERT INTO integrity_children (child_id, parent_id) VALUES (1, $nonExistentParentId)"
            }.rowsUpdated()
        }.isInstanceOf(DataIntegrityViolationException::class)
    }

    @Test
    fun `not null constraint violation throws DataIntegrityViolationException`() = runTest {
        // given
        val parentId: Int? = null

        // when & then
        assertFailure {
            kueryClient.sql {
                +"INSERT INTO integrity_children (child_id, parent_id) VALUES (1, $parentId)"
            }.rowsUpdated()
        }.isInstanceOf(DataIntegrityViolationException::class)
    }
}
