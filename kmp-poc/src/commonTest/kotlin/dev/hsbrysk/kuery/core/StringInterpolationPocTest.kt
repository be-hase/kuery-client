package dev.hsbrysk.kuery.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * PoC: proves that the kuery-client compiler plugin rewrote the string interpolation in this
 * file on the current target. Without the rewrite, `add` / `unaryPlus` throw
 * `IllegalStateException` at runtime, so these tests cannot pass accidentally.
 */
class StringInterpolationPocTest {
    @Test
    fun `unaryPlus interpolation is rewritten into named parameters`() {
        val userId = 65
        val name = "Alice"

        val sql = Sql {
            +"SELECT * FROM users"
            +"WHERE id = $userId AND name = $name"
        }

        assertEquals("SELECT * FROM users\nWHERE id = :p0 AND name = :p1", sql.body)
        assertEquals(listOf("p0" to 65, "p1" to "Alice"), sql.parameters.map { it.name to it.value })
    }

    @Test
    fun `add interpolation is rewritten into named parameters`() {
        val email = "alice@example.com"

        val sql = Sql {
            add("SELECT * FROM users WHERE email = $email")
        }

        assertEquals("SELECT * FROM users WHERE email = :p0", sql.body)
        assertEquals(listOf("p0" to email), sql.parameters.map { it.name to it.value })
    }

    @Test
    fun `fragment without interpolation stays literal and binds nothing`() {
        val sql = Sql { +"SELECT 1" }

        assertEquals("SELECT 1", sql.body)
        assertEquals(emptyList(), sql.parameters)
    }

    @Test
    fun `null interpolation is bound as a null parameter`() {
        val value: String? = null

        val sql = Sql { +"UPDATE users SET nickname = $value" }

        assertEquals("UPDATE users SET nickname = :p0", sql.body)
        assertEquals(listOf("p0" to null), sql.parameters.map { it.name to it.value })
    }

    @Test
    fun `values helper binds every element through addUnsafe and bind`() {
        val sql = Sql {
            +"INSERT INTO users (username, email)"
            values(
                listOf(
                    listOf("user1", "user1@example.com"),
                    listOf("user2", "user2@example.com"),
                ),
            )
        }

        assertEquals("INSERT INTO users (username, email)\nVALUES (:p0, :p1), (:p2, :p3)", sql.body)
        assertEquals(
            listOf(
                "p0" to "user1",
                "p1" to "user1@example.com",
                "p2" to "user2",
                "p3" to "user2@example.com",
            ),
            sql.parameters.map { it.name to it.value },
        )
    }
}
