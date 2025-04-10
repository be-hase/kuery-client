package dev.hsbrysk.kuery.core

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

@OptIn(DelicateKueryClientApi::class)
class SqlTest {
    @Test
    fun `simple create`() {
        val sql = Sql {
            +"SELECT * FROM user"
        }
        assertThat(sql).isEqualTo(Sql("SELECT * FROM user"))
    }

    @Test
    fun `simple insert`() {
        data class User(
            val name: String,
            val age: Int,
            val address: String,
        )

        val user = User(name = "name", age = 18, address = "address")
        val sql = Sql {
            +"INSERT INTO user (name, age, address)"
            +"VALUES (${user.name}, ${user.age}, ${user.address})"
        }
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
    fun `simple update`() {
        data class User(
            val id: String,
            val name: String,
            val age: Int,
            val address: String,
        )

        val user = User(id = "id", name = "name", age = 18, address = "address")
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
    fun `simple delete`() {
        val id = "id"
        val sql = Sql {
            +"DELETE FROM user WHERE id = $id"
        }
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
    fun `select in`() {
        val ids = listOf(1, 2, 3, 4, 5)
        val sql = Sql {
            +"SELECT *"
            +"FROM user"
            +"WHERE id IN ($ids)"
        }
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
    fun `insert multi`() {
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
        val sql = Sql {
            +"INSERT INTO user (name, age, address)"
            values(users) { listOf(it.name, it.age, it.address) }
        }
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
    fun `select complex condition`() {
        data class UserFilter(
            val id: String,
            val name: String?,
            val age: Int?,
            val address: String?,
        )

        val filter = UserFilter(id = "id", name = null, age = 18, address = null)
        val sql = Sql {
            +"SELECT *"
            +"FROM user"
            +"WHERE"
            +"id = ${filter.id}"
            filter.name?.let { +"AND name = $it" }
            filter.age?.let { +"AND age = $it" }
            filter.address?.let { +"AND address = $it" }
        }
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

    @Test
    fun mixedOrder() {
        val sql = Sql {
            val line2 = "L2=${bind(2)}"
            val line1 = "L1=${bind(1)}"
            val line0 = "L0=${bind(0)}"

            +line0
            +line1
            +line2
        }
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
