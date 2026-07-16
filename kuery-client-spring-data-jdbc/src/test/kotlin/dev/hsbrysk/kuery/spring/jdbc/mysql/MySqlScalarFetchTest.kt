package dev.hsbrysk.kuery.spring.jdbc.mysql

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.single
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Common scalar-fetch idioms against MySQL: `SELECT EXISTS(...)` (which MySQL returns as
 * BIGINT 0/1) into Boolean, and `COUNT(*)` (BIGINT) into Int/Long.
 */
class MySqlScalarFetchTest {
    private val kueryClient = mysql.kueryClient()

    @BeforeEach
    fun setUp() {
        mysql.jdbcClient.sql("CREATE TABLE scalar_users (user_id INT PRIMARY KEY)").update()
        mysql.jdbcClient.sql("INSERT INTO scalar_users (user_id) VALUES (1), (2)").update()
    }

    @AfterEach
    fun tearDown() {
        mysql.jdbcClient.sql("DROP TABLE scalar_users").update()
    }

    @Test
    fun `EXISTS maps to Boolean`() {
        val exists: Boolean = kueryClient
            .sql { +"SELECT EXISTS(SELECT 1 FROM scalar_users WHERE user_id = 1)" }
            .single()
        assertThat(exists).isEqualTo(true)

        val notExists: Boolean = kueryClient
            .sql { +"SELECT EXISTS(SELECT 1 FROM scalar_users WHERE user_id = 999)" }
            .single()
        assertThat(notExists).isEqualTo(false)
    }

    @Test
    fun `COUNT maps to Long and narrows to Int`() {
        val asLong: Long = kueryClient
            .sql { +"SELECT COUNT(*) FROM scalar_users" }
            .single()
        assertThat(asLong).isEqualTo(2L)

        val asInt: Int = kueryClient
            .sql { +"SELECT COUNT(*) FROM scalar_users" }
            .single()
        assertThat(asInt).isEqualTo(2)
    }

    companion object {
        private val mysql = MySqlTestContainer
    }
}
