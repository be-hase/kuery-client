package dev.hsbrysk.kuery.sqlx4k

import dev.hsbrysk.kuery.core.values
import io.github.smyrgeorge.sqlx4k.ConnectionPool
import io.github.smyrgeorge.sqlx4k.impl.extensions.asInt
import io.github.smyrgeorge.sqlx4k.sqlite.ISQLite
import io.github.smyrgeorge.sqlx4k.sqlite.sqlite
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * PoC: end-to-end over a real sqlx4k SQLite database on every target (JVM: sqlite-jdbc
 * delegation, Native: Rust sqlx via FFI) — interpolation rewritten by the compiler plugin,
 * bound as named parameters, executed, and mapped back from rows.
 */
class Sqlx4kKueryClientPocTest {
    private suspend fun setUp(dbName: String): Pair<ISQLite, Sqlx4kKueryClient> {
        // One file per test: SQLite in-memory would need pool size 1 anyway, and separate
        // files keep the tests independent even when one of them fails midway.
        val db = sqlite(
            url = "build/poc-$dbName.db",
            options = ConnectionPool.Options.builder().maxConnections(1).build(),
        )
        val client = Sqlx4kKueryClient(db)
        client.sql { +"DROP TABLE IF EXISTS users" }.rowsUpdated()
        client.sql { +"CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT, email TEXT)" }.rowsUpdated()
        return db to client
    }

    @Test
    fun `interpolated values are bound as parameters and round-trip through sqlx4k`() = runTest {
        val (db, client) = setUp("roundtrip")
        val id = 1
        val name = "Alice"
        val email = "alice@example.com"

        val inserted = client.sql {
            +"INSERT INTO users (id, name, email) VALUES ($id, $name, $email)"
        }.rowsUpdated()
        assertEquals(1, inserted)

        val row = client.sql {
            +"SELECT id, name, email FROM users WHERE id = $id"
        }.single { Triple(it.get("id").asInt(), it.get("name").asString(), it.get("email").asString()) }
        assertEquals(Triple(1, "Alice", "alice@example.com"), row)

        db.close().getOrThrow()
    }

    @Test
    fun `interpolation binds values instead of concatenating them into the SQL text`() = runTest {
        val (db, client) = setUp("injection")
        val id = 1
        val hostile = "'; DROP TABLE users; --"

        client.sql { +"INSERT INTO users (id, name) VALUES ($id, $hostile)" }.rowsUpdated()

        // The table still exists and the hostile string is stored as a plain value.
        val name = client.sql { +"SELECT name FROM users WHERE name = $hostile" }
            .single { it.get("name").asString() }
        assertEquals(hostile, name)

        db.close().getOrThrow()
    }

    @Test
    fun `null interpolation is stored as SQL NULL`() = runTest {
        val (db, client) = setUp("null")
        // Interpolating literals (`${1}`, `${"Bob"}`) would NOT exercise binding: the frontend
        // constant-folds them into the string before the plugin runs — use variables.
        val id = 1
        val name = "Bob"
        val email: String? = null

        client.sql { +"INSERT INTO users (id, name, email) VALUES ($id, $name, $email)" }.rowsUpdated()

        val stored = client.sql { +"SELECT email FROM users WHERE id = $id" }
            .single { it.get("email").asStringOrNull() }
        assertEquals(null, stored)

        db.close().getOrThrow()
    }

    @Test
    fun `values helper inserts multiple rows through addUnsafe and bind`() = runTest {
        val (db, client) = setUp("values")
        val users = listOf(2 to "user2", 1 to "user1")

        client.sql {
            +"INSERT INTO users (id, name)"
            values(users) { listOf(it.first, it.second) }
        }.rowsUpdated()

        val names = client.sql { +"SELECT name FROM users ORDER BY id" }
            .list { it.get("name").asString() }
        assertEquals(listOf("user1", "user2"), names)

        db.close().getOrThrow()
    }

    @Test
    // Kotlin/Native rejects '@' in backticked test names ("Name contains illegal characters"),
    // hence "Record-annotated" instead of "@Record".
    fun `ksp-generated RowMapper maps rows into the Record-annotated data class`() = runTest {
        val (db, client) = setUp("generated-mapper")
        val users = listOf(
            User(id = 1, name = "user1", email = "user1@example.com"),
            User(id = 2, name = "user2", email = null),
        )

        client.sql {
            +"INSERT INTO users (id, name, email)"
            values(users) { listOf(it.id, it.name, it.email) }
        }.rowsUpdated()

        val loaded = client.sql { +"SELECT * FROM users ORDER BY id" }.list(UserRowMapper)
        assertEquals(users, loaded)

        val id = 2
        val single = client.sql { +"SELECT * FROM users WHERE id = $id" }.single(UserRowMapper)
        assertEquals(User(id = 2, name = "user2", email = null), single)

        db.close().getOrThrow()
    }

    @Test
    fun `single throws when no rows match`() = runTest {
        val (db, client) = setUp("single-empty")

        assertFailsWith<NoSuchElementException> {
            client.sql { +"SELECT * FROM users WHERE id = ${999}" }.single { it }
        }

        db.close().getOrThrow()
    }
}
