package dev.hsbrysk.kuery.spring.testing.contract.mysql

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.spring.testing.ContractDatabase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Contract shared by the jdbc and r2dbc modules. Each module runs it via a concrete subclass
 * that supplies its `ContractDatabase`.
 *
 * The generated key of a single insert is reported under a driver-specific label and type, so
 * each concrete subclass pins its own expectation via [expectedGeneratedValues] (and documents
 * its driver's behavior).
 */
abstract class MySqlGeneratedValuesContract {
    protected abstract val database: ContractDatabase

    protected abstract val expectedGeneratedValues: Map<String, Any>

    // lazy: `database` is not resolvable yet while this base class initializes.
    protected val kueryClient: KueryClient by lazy { database.kueryClient() }

    @BeforeEach
    fun setUpUsersTable() {
        database.execute(
            """
            CREATE TABLE users (
                user_id INT AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(50) NOT NULL,
                email VARCHAR(100) NOT NULL
            )
            """.trimIndent(),
        )
    }

    @AfterEach
    fun dropUsersTable() {
        database.execute("DROP TABLE IF EXISTS users")
    }

    @Test
    fun `generatedValues returns the generated key of a single insert`() = runTest {
        // given
        val username = "user1"
        val email = "user1@example.com"

        // when
        val result = kueryClient
            .sql { +"INSERT INTO users (username, email) VALUES ($username, $email)" }
            .generatedValues("user_id")

        // then
        assertThat(result).isEqualTo(expectedGeneratedValues)
    }
}
