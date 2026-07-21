package dev.hsbrysk.kuery.spring.testing.contract.postgres

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.spring.testing.ContractDatabase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Pins the library's core safety claim end-to-end against the real driver: interpolated values are
 * sent as bind parameters, so quoting/escaping characters and injection payloads are stored as
 * plain data and can never terminate the statement.
 *
 * Contract shared by the jdbc and r2dbc modules. Each module runs it via a concrete subclass
 * that supplies its `ContractDatabase`.
 */
abstract class PostgresSpecialCharacterBindingContract {
    protected abstract val database: ContractDatabase

    // lazy: `database` is not resolvable yet while this base class initializes.
    protected val kueryClient: KueryClient by lazy { database.kueryClient() }

    @BeforeEach
    fun setUpUsersTable() {
        database.execute(
            """
            CREATE TABLE users (
                user_id SERIAL PRIMARY KEY,
                username VARCHAR(100) NOT NULL
            )
            """.trimIndent(),
        )
    }

    @AfterEach
    fun dropUsersTable() {
        database.execute("DROP TABLE IF EXISTS users")
    }

    private suspend fun assertRoundTrip(value: String) {
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
    fun `an injection payload is stored as plain data and the table survives`() = runTest {
        assertRoundTrip("'; DROP TABLE users; --")
    }

    @Test
    fun `quotes and backslashes round-trip`() = runTest {
        assertRoundTrip("""O'Reilly says "hi" at c:\temp\new""")
    }

    @Test
    fun `newlines and tabs round-trip`() = runTest {
        assertRoundTrip("line1\nline2\r\n\tindented")
    }

    @Test
    fun `multibyte and emoji characters round-trip`() = runTest {
        assertRoundTrip("日本語と絵文字🚀")
    }

    @Test
    fun `a value that looks like a named parameter marker is treated as data`() = runTest {
        assertRoundTrip(":p0 OR 1=1")
    }

    @Test
    fun `a colon inside a string literal in the sql text is not parsed as a parameter marker`() = runTest {
        val record = kueryClient.sql { +"SELECT 'a:b' AS v" }.singleMap()
        assertThat(record["v"]).isEqualTo("a:b")
    }
}
