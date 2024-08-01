package dev.hsbrysk.kuery.core

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.InternalPlatformDsl.toStr
import org.junit.jupiter.api.Test

class SqlTest {
    @Test
    fun `simple create`() {
        val sql = Sql.create {
            +"SELECT * FROM user"
        }
        assertThat(sql).isEqualTo(Sql.of("SELECT * FROM user", emptyList()))
    }

    @Test
    fun `simple insert`() {
        data class User(
            val name: String,
            val age: Int,
            val address: String,
        )

        val user = User(name = "name", age = 18, address = "address")
        val sql = Sql.create {
            +"INSERT INTO user (name, age, address)"
            +"VALUES (${user.name}, ${user.age}, ${user.address})"
        }
        assertThat(sql).isEqualTo(
            Sql.of(
                """
                INSERT INTO user (name, age, address)
                VALUES (:p0, :p1, :p2)
                """.trimIndent(),
                listOf(
                    NamedSqlParameter.of("p0", user.name),
                    NamedSqlParameter.of("p1", user.age),
                    NamedSqlParameter.of("p2", user.address),
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
        val sql = Sql.create {
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
            Sql.of(
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
                    NamedSqlParameter.of("p0", user.name),
                    NamedSqlParameter.of("p1", user.age),
                    NamedSqlParameter.of("p2", user.address),
                    NamedSqlParameter.of("p3", user.id),
                ),
            ),
        )
    }

    @Test
    fun `simple delete`() {
        val id = "id"
        val sql = Sql.create {
            +"DELETE FROM user WHERE id = $id"
        }
        assertThat(sql).isEqualTo(
            Sql.of(
                """
                DELETE FROM user WHERE id = :p0
                """.trimIndent(),
                listOf(
                    NamedSqlParameter.of("p0", "id"),
                ),
            ),
        )
    }

    @Test
    fun `select in`() {
        val ids = listOf(1, 2, 3, 4, 5)
        val sql = Sql.create {
            +"SELECT *"
            +"FROM user"
            +"WHERE id IN ($ids)"
        }
        assertThat(sql).isEqualTo(
            Sql.of(
                """
                SELECT *
                FROM user
                WHERE id IN (:p0)
                """.trimIndent(),
                listOf(
                    NamedSqlParameter.of("p0", ids),
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
        val sql = Sql.create {
            +"INSERT INTO user (name, age, address)"
            values(users) { listOf(it.name, it.age, it.address) }
        }
        assertThat(sql).isEqualTo(
            Sql.of(
                """
                INSERT INTO user (name, age, address)
                VALUES (:p0, :p1, :p2), (:p3, :p4, :p5), (:p6, :p7, :p8)
                """.trimIndent(),
                users.flatMapIndexed { i, user ->
                    listOf(
                        NamedSqlParameter.of("p${(i * 3)}", user.name),
                        NamedSqlParameter.of("p${(i * 3 + 1)}", user.age),
                        NamedSqlParameter.of("p${(i * 3) + 2}", user.address),
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
        val sql = Sql.create {
            +"SELECT *"
            +"FROM user"
            +"WHERE"
            +"id = ${filter.id}"
            filter.name?.let { +"AND name = $it" }
            filter.age?.let { +"AND age = $it" }
            filter.address?.let { +"AND address = $it" }
        }
        assertThat(sql).isEqualTo(
            Sql.of(
                """
                SELECT *
                FROM user
                WHERE
                id = :p0
                AND age = :p1
                """.trimIndent(),
                listOf(
                    NamedSqlParameter.of("p0", filter.id),
                    NamedSqlParameter.of("p1", filter.age),
                ),
            ),
        )
    }

    @Test
    fun mixedOrder() {
        val sql = Sql.create {
            val line2 = "L2=${bind(2)}"
            val line1 = "L1=${bind(1)}"
            val line0 = "L0=${bind(0)}"

            +line0
            +line1
            +line2
        }
        assertThat(sql).isEqualTo(
            Sql.of(
                """
                L0=:p2
                L1=:p1
                L2=:p0
                """.trimIndent(),
                listOf(
                    NamedSqlParameter.of("p0", 2),
                    NamedSqlParameter.of("p1", 1),
                    NamedSqlParameter.of("p2", 0),
                ),
            ),
        )
    }

    @Test
    fun `int string interpolation`() {
        // In such cases, string interpolation will not be executed.
        val sql1 = Sql.create {
            +"SELECT * FROM user WHERE user_id = ${1}"
        }
        assertThat(sql1).isEqualTo(
            Sql.of(
                "SELECT * FROM user WHERE user_id = 1",
                emptyList(),
            ),
        )

        // On the other hand, in such cases, it will be executed.
        val sql2 = Sql.create {
            +"SELECT * FROM user WHERE user_id = ${1 + 1}"
        }
        assertThat(sql2).isEqualTo(
            Sql.of(
                "SELECT * FROM user WHERE user_id = :p0",
                listOf(NamedSqlParameter.of("p0", 2)),
            ),
        )
    }

    @Test
    fun `string string interpolation`() {
        // In such cases, string interpolation will not be executed.
        val sql1 = Sql.create {
            +"SELECT * FROM user WHERE user_id = ${"hoge"}"
        }
        assertThat(sql1).isEqualTo(
            Sql.of(
                "SELECT * FROM user WHERE user_id = hoge",
                emptyList(),
            ),
        )

        // On the other hand, in such cases, it will be executed.
        val sql2 = Sql.create {
            +"SELECT * FROM user WHERE user_id = ${"hoge".removePrefix("h").removePrefix("o")}"
        }
        assertThat(sql2).isEqualTo(
            Sql.of(
                "SELECT * FROM user WHERE user_id = :p0",
                listOf(NamedSqlParameter.of("p0", "ge")),
            ),
        )
    }

    @Test
    fun `boolean string interpolation`() {
        // In such cases, string interpolation will not be executed.
        val sql1 = Sql.create {
            +"SELECT * FROM user WHERE user_id = ${true}"
        }
        assertThat(sql1).isEqualTo(
            Sql.of(
                "SELECT * FROM user WHERE user_id = true",
                emptyList(),
            ),
        )

        // On the other hand, in such cases, it will be executed.
        val sql2 = Sql.create {
            +"SELECT * FROM user WHERE user_id = ${true && true}"
        }
        assertThat(sql2).isEqualTo(
            Sql.of(
                "SELECT * FROM user WHERE user_id = :p0",
                listOf(NamedSqlParameter.of("p0", true)),
            ),
        )
    }

    @Test
    fun `null string interpolation`() {
        // In such cases, string interpolation will not be executed.
        val sql1 = Sql.create {
            +"SELECT * FROM user WHERE user_id = ${null}"
        }
        assertThat(sql1).isEqualTo(
            Sql.of(
                "SELECT * FROM user WHERE user_id = null",
                emptyList(),
            ),
        )

        // On the other hand, in such cases, it will be executed.
        val sql2 = Sql.create {
            +"SELECT * FROM user WHERE user_id = ${null.toStr()}"
        }
        assertThat(sql2).isEqualTo(
            Sql.of(
                "SELECT * FROM user WHERE user_id = :p0",
                listOf(NamedSqlParameter.of("p0", "null")),
            ),
        )
    }
}
