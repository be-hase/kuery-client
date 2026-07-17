package dev.hsbrysk.kuery.spring.jdbc.mysql

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Pins the library's core safety claim end-to-end against the real driver: interpolated values are
 * sent as bind parameters, so quoting/escaping characters and injection payloads are stored as
 * plain data and can never terminate the statement.
 */
class MySqlSpecialCharacterBindingTest {
    private val kueryClient = mysql.kueryClient()

    @BeforeEach
    fun setUp() {
        mysql.jdbcClient.sql(
            """
            CREATE TABLE users (
                user_id INT AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(100) NOT NULL
            )
            """.trimIndent(),
        ).update()
    }

    @AfterEach
    fun tearDown() {
        mysql.jdbcClient.sql("DROP TABLE users").update()
    }

    private fun assertRoundTrip(value: String) {
        val count = kueryClient
            .sql { +"INSERT INTO users (username) VALUES ($value)" }
            .rowsUpdated()
        assertThat(count).isEqualTo(1L)

        val record = kueryClient
            .sql { +"SELECT username FROM users WHERE username = $value" }
            .singleMap()
        assertThat(record["username"]).isEqualTo(value)
    }

    @Test
    fun `an injection payload is stored as plain data and the table survives`() {
        assertRoundTrip("'; DROP TABLE users; --")
    }

    @Test
    fun `quotes and backslashes round-trip`() {
        assertRoundTrip("""O'Reilly says "hi" at c:\temp\new""")
    }

    @Test
    fun `newlines and tabs round-trip`() {
        assertRoundTrip("line1\nline2\r\n\tindented")
    }

    @Test
    fun `multibyte and emoji characters round-trip`() {
        assertRoundTrip("日本語と絵文字🚀")
    }

    @Test
    fun `a value that looks like a named parameter marker is treated as data`() {
        assertRoundTrip(":p0 OR 1=1")
    }

    @Test
    fun `a colon inside a string literal in the sql text is not parsed as a parameter marker`() {
        val record = kueryClient.sql { +"SELECT 'a:b' AS v" }.singleMap()
        assertThat(record["v"]).isEqualTo("a:b")
    }

    companion object {
        private val mysql = MySqlTestContainer
    }
}
