package dev.hsbrysk.kuery.spring.r2dbc

import assertk.assertFailure
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.message
import assertk.assertions.startsWith
import dev.hsbrysk.kuery.core.list
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.awaitRowsUpdated

/**
 * Kotlin value classes are NOT supported as fetch types: DataClassRowMapper cannot resolve the
 * parameter names of their (private, boxed) constructor. Use the underlying type and wrap it
 * yourself, or a regular data class. This pins the current limitation.
 */
class ValueClassFetchTest {
    @JvmInline
    value class UserName(val value: String)

    private val kueryClient = h2.kueryClient()

    @BeforeEach
    fun beforeEach() = runTest {
        h2.setUpForConverterTest()
        h2.databaseClient.sql("INSERT INTO converter (text) VALUES ('user1'), ('user2')").fetch().awaitRowsUpdated()
    }

    @AfterEach
    fun afterEach() = runTest {
        h2.tearDownForConverterTest()
    }

    @Test
    fun `value class is not supported as a fetch type`() = runTest {
        assertFailure {
            kueryClient.sql {
                +"SELECT text AS value FROM converter ORDER BY id"
            }.list<UserName>()
        }.isInstanceOf(IllegalStateException::class)
            .message().isNotNull().startsWith("Cannot resolve parameter names")
    }

    companion object {
        private val h2 = H2TestDatabase()
    }
}
