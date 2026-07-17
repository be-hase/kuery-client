package dev.hsbrysk.kuery.core

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

@OptIn(DelicateKueryClientApi::class)
class SqlTest {
    @Test
    fun `a plain fragment becomes the SQL body with no parameters`() {
        val sql = Sql {
            +"SELECT * FROM user"
        }
        assertThat(sql).isEqualTo(Sql("SELECT * FROM user"))
    }

    @Test
    fun `interpolated values are bound as ordered named parameters`() {
        // given
        data class User(
            val name: String,
            val age: Int,
            val address: String,
        )

        val user = User(name = "name", age = 18, address = "address")

        // when
        val sql = Sql {
            +"INSERT INTO user (name, age, address)"
            +"VALUES (${user.name}, ${user.age}, ${user.address})"
        }

        // then
        assertThat(sql).isEqualTo(
            Sql(
                """
                INSERT INTO user (name, age, address)
                VALUES (:p0, :p1, :p2)
                """.trimIndent(),
                listOf(
                    NamedSqlParameter("p0", user.name),
                    NamedSqlParameter("p1", user.age),
                    NamedSqlParameter("p2", user.address),
                ),
            ),
        )
    }

    @Test
    fun `a multi-line template keeps its shape while values become placeholders`() {
        // given
        data class User(
            val id: String,
            val name: String,
            val age: Int,
            val address: String,
        )

        val user = User(id = "id", name = "name", age = 18, address = "address")

        // when
        val sql = Sql {
            add(
                """
                UPDATE user
                SET
                    name = ${user.name},
                    age = ${user.age},
                    address = ${user.address}
                WHERE
                    id = ${user.id}
                """.trimIndent(),
            )
        }

        // then
        assertThat(sql).isEqualTo(
            Sql(
                """
                UPDATE user
                SET
                    name = :p0,
                    age = :p1,
                    address = :p2
                WHERE
                    id = :p3
                """.trimIndent(),
                listOf(
                    NamedSqlParameter("p0", user.name),
                    NamedSqlParameter("p1", user.age),
                    NamedSqlParameter("p2", user.address),
                    NamedSqlParameter("p3", user.id),
                ),
            ),
        )
    }

    @Test
    fun `an interpolated value in a single-line fragment is bound as a parameter`() {
        // given
        val id = "id"

        // when
        val sql = Sql {
            +"DELETE FROM user WHERE id = $id"
        }

        // then
        assertThat(sql).isEqualTo(
            Sql(
                """
                DELETE FROM user WHERE id = :p0
                """.trimIndent(),
                listOf(
                    NamedSqlParameter("p0", "id"),
                ),
            ),
        )
    }

    @Test
    fun `a collection value in an IN clause is bound as a single parameter`() {
        // given
        val ids = listOf(1, 2, 3, 4, 5)

        // when
        val sql = Sql {
            +"SELECT *"
            +"FROM user"
            +"WHERE id IN ($ids)"
        }

        // then
        assertThat(sql).isEqualTo(
            Sql(
                """
                SELECT *
                FROM user
                WHERE id IN (:p0)
                """.trimIndent(),
                listOf(
                    NamedSqlParameter("p0", ids),
                ),
            ),
        )
    }

    @Test
    fun `an empty collection is bound as a single parameter without expansion`() {
        // An empty collection is NOT expanded at the core level. It is bound as a single
        // parameter whose value is the empty collection; what happens next is up to the
        // executing layer (e.g. Spring's named parameter expansion).

        // given
        val emptyIds = emptyList<Int>()

        // when
        val sql = Sql {
            +"SELECT *"
            +"FROM user"
            +"WHERE id IN ($emptyIds)"
        }

        // then
        assertThat(sql).isEqualTo(
            Sql(
                """
                SELECT *
                FROM user
                WHERE id IN (:p0)
                """.trimIndent(),
                listOf(
                    NamedSqlParameter("p0", emptyIds),
                ),
            ),
        )

        // given
        val emptyIdSet = emptySet<Int>()

        // when
        val sql2 = Sql {
            +"SELECT * FROM user WHERE id IN ($emptyIdSet)"
        }

        // then
        assertThat(sql2).isEqualTo(
            Sql(
                "SELECT * FROM user WHERE id IN (:p0)",
                listOf(
                    NamedSqlParameter("p0", emptyIdSet),
                ),
            ),
        )
    }

    @Test
    fun `a cast suffix immediately after a placeholder is kept verbatim`() {
        // PostgreSQL style casts like `$value::jsonb` must keep the cast suffix verbatim
        // right after the generated placeholder.

        // given
        val json = """{"theme":"dark"}"""
        val id = "id"

        // when
        val sql = Sql {
            +"UPDATE user SET settings = $json::jsonb WHERE id = $id::uuid"
        }

        // then
        assertThat(sql).isEqualTo(
            Sql(
                "UPDATE user SET settings = :p0::jsonb WHERE id = :p1::uuid",
                listOf(
                    NamedSqlParameter("p0", json),
                    NamedSqlParameter("p1", id),
                ),
            ),
        )
    }

    @Test
    fun `values expands rows into consecutively numbered parameters`() {
        // given
        data class User(
            val name: String,
            val age: Int,
            val address: String,
        )

        val users = listOf(
            User(name = "name1", age = 1, address = "address1"),
            User(name = "name2", age = 2, address = "address2"),
            User(name = "name3", age = 3, address = "address3"),
        )

        // when
        val sql = Sql {
            +"INSERT INTO user (name, age, address)"
            values(users) { listOf(it.name, it.age, it.address) }
        }

        // then
        assertThat(sql).isEqualTo(
            Sql(
                """
                INSERT INTO user (name, age, address)
                VALUES (:p0, :p1, :p2), (:p3, :p4, :p5), (:p6, :p7, :p8)
                """.trimIndent(),
                users.flatMapIndexed { i, user ->
                    listOf(
                        NamedSqlParameter("p${(i * 3)}", user.name),
                        NamedSqlParameter("p${(i * 3 + 1)}", user.age),
                        NamedSqlParameter("p${(i * 3) + 2}", user.address),
                    )
                },
            ),
        )
    }

    @Test
    fun `conditional lines appear only for non-null values with consecutive numbering`() {
        // given
        data class UserFilter(
            val id: String,
            val name: String?,
            val age: Int?,
            val address: String?,
        )

        val filter = UserFilter(id = "id", name = null, age = 18, address = null)

        // when
        val sql = Sql {
            +"SELECT *"
            +"FROM user"
            +"WHERE"
            +"id = ${filter.id}"
            filter.name?.let { +"AND name = $it" }
            filter.age?.let { +"AND age = $it" }
            filter.address?.let { +"AND address = $it" }
        }

        // then
        assertThat(sql).isEqualTo(
            Sql(
                """
                SELECT *
                FROM user
                WHERE
                id = :p0
                AND age = :p1
                """.trimIndent(),
                listOf(
                    NamedSqlParameter("p0", filter.id),
                    NamedSqlParameter("p1", filter.age),
                ),
            ),
        )
    }

    @Suppress("KUERY_UNSAFE_SQL_STRING")
    @Test
    fun `interpolation and manual bind share a single parameter counter`() {
        // Interpolation and manual bind() share a single parameter counter, so numbering
        // never collides. Binding the same value twice yields two distinct parameters.

        // given
        val userId = "user1"
        val mask = 0b0100
        val age = 18

        // when
        val sql = Sql {
            +"SELECT * FROM user WHERE id = $userId"
            addUnsafe("AND permission & ${bind(mask)} = ${bind(mask)}")
            +"AND age = $age"
        }

        // then
        assertThat(sql).isEqualTo(
            Sql(
                """
                SELECT * FROM user WHERE id = :p0
                AND permission & :p1 = :p2
                AND age = :p3
                """.trimIndent(),
                listOf(
                    NamedSqlParameter("p0", userId),
                    NamedSqlParameter("p1", mask),
                    NamedSqlParameter("p2", mask),
                    NamedSqlParameter("p3", age),
                ),
            ),
        )
    }

    @Suppress("KUERY_UNSAFE_SQL_STRING")
    @Test
    fun `parameter numbering follows bind evaluation order, not line order`() {
        // when
        val sql = Sql {
            val line2 = "L2=${bind(2)}"
            val line1 = "L1=${bind(1)}"
            val line0 = "L0=${bind(0)}"

            +line0
            +line1
            +line2
        }

        // then
        assertThat(sql).isEqualTo(
            Sql(
                """
                L0=:p2
                L1=:p1
                L2=:p0
                """.trimIndent(),
                listOf(
                    NamedSqlParameter("p0", 2),
                    NamedSqlParameter("p1", 1),
                    NamedSqlParameter("p2", 0),
                ),
            ),
        )
    }
}
