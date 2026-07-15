package dev.hsbrysk.kuery.spring.r2dbc.mysql

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import dev.hsbrysk.kuery.core.flow
import dev.hsbrysk.kuery.core.single
import dev.hsbrysk.kuery.core.singleOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.TypeMismatchDataAccessException
import org.springframework.r2dbc.core.awaitRowsUpdated

class MySqlNullScalarResultTest {
    private val kueryClient = mysql.kueryClient()

    @BeforeEach
    fun setUp() = runTest {
        mysql.databaseClient.sql(
            """
            CREATE TABLE users (
                user_id INT AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(50),
                email VARCHAR(100) NOT NULL,
                age INT
            )
            """.trimIndent(),
        ).fetch().awaitRowsUpdated()
    }

    @AfterEach
    fun tearDown() = runTest {
        mysql.databaseClient.sql("DROP TABLE users").fetch().awaitRowsUpdated()
    }

    @Test
    fun `singleOrNull returns null for a NULL column`() = runTest {
        insert("user1@example.com", age = null)
        val age: Int? = kueryClient.sql { +"SELECT age FROM users" }.singleOrNull()
        assertThat(age).isNull()
    }

    @Test
    fun `singleOrNull returns null for a NULL aggregate over an empty table`() = runTest {
        val maxAge: Int? = kueryClient.sql { +"SELECT max(age) FROM users" }.singleOrNull()
        assertThat(maxAge).isNull()
    }

    @Test
    fun `singleOrNull returns null for a NULL string column`() = runTest {
        insert("user1@example.com", age = null)
        val username: String? = kueryClient.sql { +"SELECT username FROM users" }.singleOrNull()
        assertThat(username).isNull()
    }

    @Test
    fun `single throws for a NULL column`() = runTest {
        insert("user1@example.com", age = null)
        assertFailure {
            kueryClient.sql { +"SELECT age FROM users" }.single<Int>()
        }.isInstanceOf(TypeMismatchDataAccessException::class)
    }

    @Test
    fun `list preserves NULL elements`() = runTest {
        insert("user1@example.com", age = 20)
        insert("user2@example.com", age = null)
        val ages: List<Int?> = kueryClient.sql { +"SELECT age FROM users ORDER BY user_id" }.list(Int::class)
        assertThat(ages).isEqualTo(listOf(20, null))
    }

    @Test
    fun `flow preserves NULL elements`() = runTest {
        insert("user1@example.com", age = 20)
        insert("user2@example.com", age = null)
        val ages: List<Int?> = kueryClient.sql { +"SELECT age FROM users ORDER BY user_id" }.flow<Int>().toList()
        assertThat(ages).isEqualTo(listOf(20, null))
    }

    private suspend fun insert(
        email: String,
        age: Int?,
    ) {
        kueryClient.sql { +"INSERT INTO users (email, age) VALUES ($email, $age)" }.rowsUpdated()
    }

    companion object {
        private val mysql = MySqlTestContainer
    }
}
