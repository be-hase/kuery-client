package dev.hsbrysk.kuery.spring.jdbc

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import dev.hsbrysk.kuery.core.single
import dev.hsbrysk.kuery.core.singleOrNull
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.TypeMismatchDataAccessException

class PostgresNullScalarResultTest {
    private val kueryClient = postgres.kueryClient()

    @BeforeEach
    fun setUp() {
        postgres.jdbcClient.sql(
            """
            CREATE TABLE users (
                user_id SERIAL PRIMARY KEY,
                username VARCHAR(50),
                email VARCHAR(100) NOT NULL,
                age INT
            )
            """.trimIndent(),
        ).update()
    }

    @AfterEach
    fun tearDown() {
        postgres.jdbcClient.sql("DROP TABLE users").update()
    }

    @Test
    fun `singleOrNull returns null for a NULL column`() {
        insert("user1@example.com", age = null)
        val age: Int? = kueryClient.sql { +"SELECT age FROM users" }.singleOrNull()
        assertThat(age).isNull()
    }

    @Test
    fun `singleOrNull returns null for a NULL aggregate over an empty table`() {
        val maxAge: Int? = kueryClient.sql { +"SELECT max(age) FROM users" }.singleOrNull()
        assertThat(maxAge).isNull()
    }

    @Test
    fun `singleOrNull returns null for a NULL string column`() {
        insert("user1@example.com", age = null)
        val username: String? = kueryClient.sql { +"SELECT username FROM users" }.singleOrNull()
        assertThat(username).isNull()
    }

    @Test
    fun `single throws for a NULL column`() {
        insert("user1@example.com", age = null)
        assertFailure {
            kueryClient.sql { +"SELECT age FROM users" }.single<Int>()
        }.isInstanceOf(TypeMismatchDataAccessException::class)
    }

    @Test
    fun `list preserves NULL elements`() {
        insert("user1@example.com", age = 20)
        insert("user2@example.com", age = null)
        val ages: List<Int?> = kueryClient.sql { +"SELECT age FROM users ORDER BY user_id" }.list(Int::class)
        assertThat(ages).isEqualTo(listOf(20, null))
    }

    @Test
    fun `sequence preserves NULL elements`() {
        insert("user1@example.com", age = 20)
        insert("user2@example.com", age = null)
        val ages: List<Int?> = kueryClient.sql { +"SELECT age FROM users ORDER BY user_id" }
            .sequence(Int::class)
            .use { it.toList() }
        assertThat(ages).isEqualTo(listOf(20, null))
    }

    private fun insert(
        email: String,
        age: Int?,
    ) {
        kueryClient.sql { +"INSERT INTO users (email, age) VALUES ($email, $age)" }.rowsUpdated()
    }

    companion object {
        private val postgres = PostgresTestContainer()

        @JvmStatic
        @AfterAll
        fun afterAll() {
            postgres.close()
        }
    }
}
