package dev.hsbrysk.kuery.spring.testing.contract.mysql

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.spring.testing.ContractDatabase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Contract shared by the jdbc and r2dbc modules. Each module runs it via a concrete subclass
 * that supplies its `ContractDatabase`. Binding a null via a module-specific typed parameter
 * (an escape hatch) is covered on each concrete subclass since the API differs per module.
 */
abstract class MySqlNullBindingContract {
    protected abstract val database: ContractDatabase

    // lazy: `database` is not resolvable yet while this base class initializes.
    protected val kueryClient: KueryClient by lazy { database.kueryClient() }

    @BeforeEach
    fun setUpUsersTable() {
        database.execute(
            """
            CREATE TABLE users (
                user_id INT AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(50),
                email VARCHAR(100) NOT NULL,
                age INT
            )
            """.trimIndent(),
        )
    }

    @AfterEach
    fun dropUsersTable() {
        database.execute("DROP TABLE IF EXISTS users")
    }

    @Test
    fun `bind non-null value`() = runTest {
        // given
        val username = "user1"
        val email = "user1@example.com"

        // when
        val count = kueryClient
            .sql { +"INSERT INTO users (username, email) VALUES ($username, $email)" }
            .rowsUpdated()

        // then
        assertThat(count).isEqualTo(1L)
    }

    @Test
    fun `bind null value`() = runTest {
        // given
        val username: String? = null
        val email = "user1@example.com"

        // when
        val count = kueryClient
            .sql { +"INSERT INTO users (username, email) VALUES ($username, $email)" }
            .rowsUpdated()

        // then
        assertThat(count).isEqualTo(1L)

        val record = kueryClient
            .sql { +"SELECT * FROM users WHERE email = $email" }
            .singleMap()
        assertThat(record["username"]).isNull()
    }

    @Test
    fun `bind null value to an int column`() = runTest {
        // given
        val age: Int? = null
        val email = "user1@example.com"

        // when
        val count = kueryClient
            .sql { +"INSERT INTO users (email, age) VALUES ($email, $age)" }
            .rowsUpdated()

        // then
        assertThat(count).isEqualTo(1L)
    }

    @Test
    fun `compare with null value in WHERE clause`() = runTest {
        // given
        val username: String? = null

        // when
        val list = kueryClient
            .sql { +"SELECT * FROM users WHERE username = $username" }
            .listMap()

        // then
        assertThat(list).isEqualTo(emptyList())
    }

    @Test
    fun `select null value directly`() = runTest {
        // given
        val value: String? = null

        // when
        val record = kueryClient
            .sql { +"SELECT $value AS v" }
            .singleMap()

        // then
        assertThat(record["v"]).isNull()
    }
}
