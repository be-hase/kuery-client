package dev.hsbrysk.kuery.spring.testing.contract.mysql

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.core.flow
import dev.hsbrysk.kuery.core.single
import dev.hsbrysk.kuery.core.singleOrNull
import dev.hsbrysk.kuery.spring.testing.ContractDatabase
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.TypeMismatchDataAccessException

/**
 * Contract shared by the jdbc and r2dbc modules. Each module runs it via a concrete subclass
 * that supplies its `ContractDatabase`.
 */
abstract class MySqlNullScalarResultContract {
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
    fun `singleOrNull returns null for a NULL column`() = runTest {
        // given
        insert("user1@example.com", age = null)

        // when
        val age: Int? = kueryClient.sql { +"SELECT age FROM users" }.singleOrNull()

        // then
        assertThat(age).isNull()
    }

    @Test
    fun `singleOrNull returns null for a NULL aggregate over an empty table`() = runTest {
        val maxAge: Int? = kueryClient.sql { +"SELECT max(age) FROM users" }.singleOrNull()
        assertThat(maxAge).isNull()
    }

    @Test
    fun `singleOrNull returns null for a NULL string column`() = runTest {
        // given
        insert("user1@example.com", age = null)

        // when
        val username: String? = kueryClient.sql { +"SELECT username FROM users" }.singleOrNull()

        // then
        assertThat(username).isNull()
    }

    @Test
    fun `single throws for a NULL column`() = runTest {
        // given
        insert("user1@example.com", age = null)

        // when & then
        assertFailure {
            kueryClient.sql { +"SELECT age FROM users" }.single<Int>()
        }.isInstanceOf(TypeMismatchDataAccessException::class)
    }

    @Test
    fun `list preserves NULL elements`() = runTest {
        // given
        insert("user1@example.com", age = 20)
        insert("user2@example.com", age = null)

        // when
        val ages: List<Int?> = kueryClient.sql { +"SELECT age FROM users ORDER BY user_id" }.list(Int::class)

        // then
        assertThat(ages).isEqualTo(listOf(20, null))
    }

    @Test
    fun `flow preserves NULL elements`() = runTest {
        // given
        insert("user1@example.com", age = 20)
        insert("user2@example.com", age = null)

        // when
        val ages: List<Int?> = kueryClient.sql { +"SELECT age FROM users ORDER BY user_id" }.flow<Int>().toList()

        // then
        assertThat(ages).isEqualTo(listOf(20, null))
    }

    private suspend fun insert(
        email: String,
        age: Int?,
    ) {
        kueryClient.sql { +"INSERT INTO users (email, age) VALUES ($email, $age)" }.rowsUpdated()
    }
}
