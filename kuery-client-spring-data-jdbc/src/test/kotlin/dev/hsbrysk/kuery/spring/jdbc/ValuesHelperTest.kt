package dev.hsbrysk.kuery.spring.jdbc

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.list
import dev.hsbrysk.kuery.core.values
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ValuesHelperTest {
    private val kueryClient = mysql.kueryClient()

    @BeforeEach
    fun setUp() {
        mysql.jdbcClient.sql(
            """
            CREATE TABLE users (
                user_id INT AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(50) NOT NULL,
                email VARCHAR(100),
                age INT NOT NULL
            )
            """.trimIndent(),
        ).update()
    }

    @AfterEach
    fun testDown() {
        mysql.jdbcClient.sql(
            """
            DROP TABLE users
            """.trimIndent(),
        ).update()
    }

    data class User(val userId: Int, val username: String, val email: String?, val age: Int)

    @Test
    fun test() {
        val input = listOf(
            listOf("user1", "user1@example.com", 1),
            listOf("user2", null, 2),
            listOf("user3", "user3@example.com", 3),
        )

        val rowsUpdated = kueryClient.sql {
            +"INSERT INTO users (username, email, age) ${values(input)}"
        }.rowsUpdated()
        assertThat(rowsUpdated).isEqualTo(3)

        val users: List<User> = kueryClient.sql {
            +"SELECT * FROM users"
        }.list()
        assertThat(users).isEqualTo(
            listOf(
                User(1, "user1", "user1@example.com", 1),
                User(2, "user2", null, 2),
                User(3, "user3", "user3@example.com", 3),
            ),
        )
    }

    @Test
    fun `test with transformer`() {
        data class UserParam(val username: String, val email: String?, val age: Int)

        val input = listOf(
            UserParam("user1", "user1@example.com", 1),
            UserParam("user2", null, 2),
            UserParam("user3", "user3@example.com", 3),
        )

        val rowsUpdated = kueryClient.sql {
            +"INSERT INTO users (username, email, age)"
            +values(input) { listOf(it.username, it.email, it.age) }
        }.rowsUpdated()
        assertThat(rowsUpdated).isEqualTo(3)

        val users: List<User> = kueryClient.sql {
            +"SELECT * FROM users"
        }.list()
        assertThat(users).isEqualTo(
            listOf(
                User(1, "user1", "user1@example.com", 1),
                User(2, "user2", null, 2),
                User(3, "user3", "user3@example.com", 3),
            ),
        )
    }

    companion object {
        private val mysql = MySqlTestContainer()

        @AfterAll
        @JvmStatic
        fun afterAll() {
            mysql.close()
        }
    }
}
