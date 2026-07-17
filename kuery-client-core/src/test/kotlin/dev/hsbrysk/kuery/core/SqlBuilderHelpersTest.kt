package dev.hsbrysk.kuery.core

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import org.junit.jupiter.api.Test

@OptIn(DelicateKueryClientApi::class)
class SqlBuilderHelpersTest {
    @Test
    fun `values with a single row appends a single VALUES group`() {
        // given
        val input = listOf(
            listOf("user0", "user0@example.com", 1),
        )

        // when
        val result = Sql {
            addUnsafe("INSERT INTO users (userid, email, age)")
            values(input)
        }

        // then
        assertThat(result.body)
            .isEqualTo("INSERT INTO users (userid, email, age)\nVALUES (:p0, :p1, :p2)")
        assertThat(result.parameters).isEqualTo(
            listOf(
                NamedSqlParameter("p0", "user0"),
                NamedSqlParameter("p1", "user0@example.com"),
                NamedSqlParameter("p2", 1),
            ),
        )
    }

    @Test
    fun `values with multiple rows appends comma-separated VALUES groups`() {
        // given
        val input = listOf(
            listOf("user0", "user0@example.com", 1),
            listOf("user1", null, 2),
            listOf("user2", "user2@example.com", 3),
        )

        // when
        val result = Sql {
            addUnsafe("INSERT INTO users (userid, email, age)")
            values(input)
        }

        // then
        assertThat(result.body)
            .isEqualTo(
                "INSERT INTO users (userid, email, age)\nVALUES (:p0, :p1, :p2), (:p3, :p4, :p5), (:p6, :p7, :p8)",
            )
        assertThat(result.parameters).isEqualTo(
            listOf(
                NamedSqlParameter("p0", "user0"),
                NamedSqlParameter("p1", "user0@example.com"),
                NamedSqlParameter("p2", 1),
                NamedSqlParameter("p3", "user1"),
                NamedSqlParameter("p4", null),
                NamedSqlParameter("p5", 2),
                NamedSqlParameter("p6", "user2"),
                NamedSqlParameter("p7", "user2@example.com"),
                NamedSqlParameter("p8", 3),
            ),
        )
    }

    @Test
    fun `values throws IllegalArgumentException for an empty list`() {
        // when & then
        assertFailure {
            Sql {
                addUnsafe("INSERT INTO users (userid, email, age)")
                values(emptyList<List<Any>>())
            }
        }.isInstanceOf(IllegalArgumentException::class)
    }

    @Test
    fun `values throws IllegalArgumentException when a row is empty`() {
        // given
        val input = listOf(
            listOf<Any>(),
        )

        // when & then
        assertFailure {
            Sql {
                addUnsafe("INSERT INTO users (userid, email, age)")
                values(input)
            }
        }.isInstanceOf(IllegalArgumentException::class)
    }

    @Test
    fun `values throws IllegalArgumentException when rows have different sizes`() {
        // given
        val input = listOf(
            listOf("user0", "user0@example.com", 1),
            listOf("user1", null),
            listOf("user2", "user2@example.com", 3),
        )

        // when & then
        assertFailure {
            Sql {
                addUnsafe("INSERT INTO users (userid, email, age)")
                values(input)
            }
        }.isInstanceOf(IllegalArgumentException::class)
    }

    @Test
    fun `values with a transformer maps each element to a row of bind values`() {
        // given
        data class UserParam(
            val userid: String,
            val email: String?,
            val age: Int,
        )

        val input = listOf(
            UserParam("user0", "user0@example.com", 1),
            UserParam("user1", null, 2),
            UserParam("user2", "user2@example.com", 3),
        )

        // when
        val result = Sql {
            addUnsafe("INSERT INTO users (userid, email, age)")
            values(input) { listOf(it.userid, it.email, it.age) }
        }

        // then
        assertThat(result.body)
            .isEqualTo(
                "INSERT INTO users (userid, email, age)\nVALUES (:p0, :p1, :p2), (:p3, :p4, :p5), (:p6, :p7, :p8)",
            )
        assertThat(result.parameters).isEqualTo(
            listOf(
                NamedSqlParameter("p0", "user0"),
                NamedSqlParameter("p1", "user0@example.com"),
                NamedSqlParameter("p2", 1),
                NamedSqlParameter("p3", "user1"),
                NamedSqlParameter("p4", null),
                NamedSqlParameter("p5", 2),
                NamedSqlParameter("p6", "user2"),
                NamedSqlParameter("p7", "user2@example.com"),
                NamedSqlParameter("p8", 3),
            ),
        )
    }
}
