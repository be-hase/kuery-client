package dev.hsbrysk.kuery.spring.jdbc.mysql

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.SqlParameterValue
import java.sql.Types

class NullBindingTest {
    private val kueryClient = mysql.kueryClient()

    @BeforeEach
    fun setUp() {
        mysql.jdbcClient.sql(
            """
            CREATE TABLE users (
                user_id INT AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(50),
                email VARCHAR(100) NOT NULL,
                age INT
            )
            """.trimIndent(),
        ).update()
    }

    @AfterEach
    fun tearDown() {
        mysql.jdbcClient.sql("DROP TABLE users").update()
    }

    @Test
    fun `bind non-null value`() {
        val username = "user1"
        val email = "user1@example.com"
        val count = kueryClient
            .sql { +"INSERT INTO users (username, email) VALUES ($username, $email)" }
            .rowsUpdated()
        assertThat(count).isEqualTo(1L)
    }

    @Test
    fun `bind null value`() {
        val username: String? = null
        val email = "user1@example.com"
        val count = kueryClient
            .sql { +"INSERT INTO users (username, email) VALUES ($username, $email)" }
            .rowsUpdated()
        assertThat(count).isEqualTo(1L)

        val record = kueryClient
            .sql { +"SELECT * FROM users WHERE email = $email" }
            .singleMap()
        assertThat(record["username"]).isNull()
    }

    @Test
    fun `bind null value to an int column`() {
        val age: Int? = null
        val email = "user1@example.com"
        val count = kueryClient
            .sql { +"INSERT INTO users (email, age) VALUES ($email, $age)" }
            .rowsUpdated()
        assertThat(count).isEqualTo(1L)
    }

    @Test
    fun `compare with null value in WHERE clause`() {
        val username: String? = null
        val list = kueryClient
            .sql { +"SELECT * FROM users WHERE username = $username" }
            .listMap()
        assertThat(list).isEqualTo(emptyList())
    }

    @Test
    fun `select null value directly`() {
        val value: String? = null
        val record = kueryClient
            .sql { +"SELECT $value AS v" }
            .singleMap()
        assertThat(record["v"]).isNull()
    }

    @Test
    fun `bind null value via typed SqlParameterValue as an escape hatch`() {
        val username = SqlParameterValue(Types.VARCHAR, null)
        val email = "user1@example.com"
        val count = kueryClient
            .sql { +"INSERT INTO users (username, email) VALUES ($username, $email)" }
            .rowsUpdated()
        assertThat(count).isEqualTo(1L)
    }

    companion object {
        private val mysql = MySqlTestContainer
    }
}
