package dev.hsbrysk.kuery.core

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import org.junit.jupiter.api.Test

@OptIn(DelicateKueryClientApi::class)
class SqlBuilderHelpersTest {
    @Test
    fun `values single`() {
        val input = listOf(
            listOf("user0", "user0@example.com", 1),
        )
        val result = Sql {
            addUnsafe("INSERT INTO users (userid, email, age)")
            values(input)
        }

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
    fun `values multi`() {
        val input = listOf(
            listOf("user0", "user0@example.com", 1),
            listOf("user1", null, 2),
            listOf("user2", "user2@example.com", 3),
        )
        val result = Sql {
            addUnsafe("INSERT INTO users (userid, email, age)")
            values(input)
        }

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
    fun `values empty`() {
        assertFailure {
            Sql {
                addUnsafe("INSERT INTO users (userid, email, age)")
                values(emptyList<List<Any>>())
            }
        }.isInstanceOf(IllegalArgumentException::class)
    }

    @Test
    fun `values child list empty`() {
        val input = listOf(
            listOf<Any>(),
        )
        assertFailure {
            Sql {
                addUnsafe("INSERT INTO users (userid, email, age)")
                values(input)
            }
        }.isInstanceOf(IllegalArgumentException::class)
    }

    @Test
    fun `values child list size is different`() {
        val input = listOf(
            listOf("user0", "user0@example.com", 1),
            listOf("user1", null),
            listOf("user2", "user2@example.com", 3),
        )
        assertFailure {
            Sql {
                addUnsafe("INSERT INTO users (userid, email, age)")
                values(input)
            }
        }.isInstanceOf(IllegalArgumentException::class)
    }

    @Test
    fun `values multi with transformer`() {
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
        val result = Sql {
            addUnsafe("INSERT INTO users (userid, email, age)")
            values(input) { listOf(it.userid, it.email, it.age) }
        }

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
