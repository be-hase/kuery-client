package dev.hsbrysk.kuery.spring.testing.contract.postgres

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.spring.testing.ContractDatabase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.dao.IncorrectResultSizeDataAccessException

/**
 * Contract shared by the jdbc and r2dbc modules. Each module runs it via a concrete subclass
 * that supplies its `ContractDatabase`.
 *
 * Pins the failure contract of [KueryClient.FetchSpec.generatedValues]:
 * EmptyResultDataAccessException when the driver reports no generated-value rows,
 * IncorrectResultSizeDataAccessException when it reports more than one. These cases are exercised
 * with PostgreSQL because its RETURNING-based generated values actually produce zero or multiple
 * rows; the MySQL drivers cannot reproduce both paths (see the subclasses of
 * [dev.hsbrysk.kuery.spring.testing.contract.mysql.MySqlGeneratedValuesContract]).
 *
 * The generated key of a single insert is reported under a driver-specific label and type, so
 * each concrete subclass pins its own expectation via [expectedGeneratedValues].
 */
abstract class PostgresGeneratedValuesContract {
    protected abstract val database: ContractDatabase

    protected abstract val expectedGeneratedValues: Map<String, Any>

    // lazy: `database` is not resolvable yet while this base class initializes.
    protected val kueryClient: KueryClient by lazy { database.kueryClient() }

    @BeforeEach
    fun setUpUsersTable() {
        database.execute(
            """
            CREATE TABLE users (
                user_id SERIAL PRIMARY KEY,
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

    @Test
    fun `generatedValues throws EmptyResultDataAccessException when no keys are generated`() = runTest {
        assertFailure {
            kueryClient
                .sql { +"UPDATE users SET username = 'updated' WHERE user_id = 1" }
                .generatedValues("user_id")
        }.isInstanceOf(EmptyResultDataAccessException::class)
    }

    @Test
    fun `generatedValues throws IncorrectResultSizeDataAccessException for multiple keys`() = runTest {
        assertFailure {
            kueryClient
                .sql {
                    +"""
                    INSERT INTO users (username, email) VALUES
                    ('user1', 'user1@example.com'),
                    ('user2', 'user2@example.com')
                    """.trimIndent()
                }
                .generatedValues("user_id")
        }.isInstanceOf(IncorrectResultSizeDataAccessException::class)
    }
}
