package dev.hsbrysk.kuery.core

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.internal.DefaultSql
import org.junit.jupiter.api.Test

class SqlTest {
    @Test
    fun of() {
        assertThat(
            Sql.of(
                "SELECT * FROM some_table",
                listOf(NamedSqlParameter.of("hoge", "hoge-value")),
            ),
        )
            .isEqualTo(
                DefaultSql(
                    "SELECT * FROM some_table",
                    listOf(NamedSqlParameter.of("hoge", "hoge-value")),
                ),
            )
    }

//    @Test
//    fun `simple create`() {
//        val sql = Sql.create {
//            +"SELECT * FROM user"
//        }
//        assertThat(sql).isEqualTo(Sql.of("SELECT * FROM user", emptyList()))
//    }
//
//    @Test
//    fun `simple insert`() {
//        data class User(
//            val name: String,
//            val age: Int,
//            val address: String,
//        )
//
//        val user = User(name = "name", age = 18, address = "address")
//        val sql = Sql.create {
//            +"INSERT INTO user (name, age, address)"
//            +"VALUES (${bind(user.name)}, ${bind(user.age)}, ${bind(user.address)})"
//        }
//        assertThat(sql).isEqualTo(
//            Sql.of(
//                """
//                INSERT INTO user (name, age, address)
//                VALUES (:p0, :p1, :p2)
//                """.trimIndent(),
//                listOf(
//                    NamedSqlParameter.of("p0", user.name),
//                    NamedSqlParameter.of("p1", user.age),
//                    NamedSqlParameter.of("p2", user.address),
//                ),
//            ),
//        )
//    }
//
//    @Test
//    fun `simple update`() {
//        data class User(
//            val id: String,
//            val name: String,
//            val age: Int,
//            val address: String,
//        )
//
//        val user = User(id = "id", name = "name", age = 18, address = "address")
//        val sql = Sql.create {
//            add(
//                """
//                UPDATE user
//                SET
//                    name = ${bind(user.name)},
//                    age = ${bind(user.age)},
//                    address = ${bind(user.address)}
//                WHERE
//                    id = ${bind(user.id)}
//                """.trimIndent(),
//            )
//        }
//        assertThat(sql).isEqualTo(
//            Sql.of(
//                """
//                UPDATE user
//                SET
//                    name = :p0,
//                    age = :p1,
//                    address = :p2
//                WHERE
//                    id = :p3
//                """.trimIndent(),
//                listOf(
//                    NamedSqlParameter.of("p0", user.name),
//                    NamedSqlParameter.of("p1", user.age),
//                    NamedSqlParameter.of("p2", user.address),
//                    NamedSqlParameter.of("p3", user.id),
//                ),
//            ),
//        )
//    }
//
//    @Test
//    fun `simple delete`() {
//        val sql = Sql.create {
//            +"DELETE FROM user WHERE id = ${bind("id")}"
//        }
//        assertThat(sql).isEqualTo(
//            Sql.of(
//                """
//                DELETE FROM user WHERE id = :p0
//                """.trimIndent(),
//                listOf(
//                    NamedSqlParameter.of("p0", "id"),
//                ),
//            ),
//        )
//    }
//
//    @Test
//    fun `select in`() {
//        val ids = listOf(1, 2, 3, 4, 5)
//        val sql = Sql.create {
//            +"SELECT *"
//            +"FROM user"
//            +"WHERE id IN (${ids.joinToString(separator = ", ") { bind(it) }})"
//        }
//        assertThat(sql).isEqualTo(
//            Sql.of(
//                """
//                SELECT *
//                FROM user
//                WHERE id IN (:p0, :p1, :p2, :p3, :p4)
//                """.trimIndent(),
//                listOf(
//                    NamedSqlParameter.of("p0", 1),
//                    NamedSqlParameter.of("p1", 2),
//                    NamedSqlParameter.of("p2", 3),
//                    NamedSqlParameter.of("p3", 4),
//                    NamedSqlParameter.of("p4", 5),
//                ),
//            ),
//        )
//    }
//
//    @Test
//    fun `insert multi`() {
//        data class User(
//            val name: String,
//            val age: Int,
//            val address: String,
//        )
//
//        val users = listOf(
//            User(name = "name1", age = 1, address = "address1"),
//            User(name = "name2", age = 2, address = "address2"),
//            User(name = "name3", age = 3, address = "address3"),
//        )
//        val sql = Sql.create {
//            +"INSERT INTO user (name, age, address)"
//            +"VALUES"
//            +users.joinToString(", ") { "(${bind(it.name)}, ${bind(it.age)}, ${bind(it.address)})" }
//        }
//        assertThat(sql).isEqualTo(
//            Sql.of(
//                """
//                INSERT INTO user (name, age, address)
//                VALUES
//                (:p0, :p1, :p2), (:p3, :p4, :p5), (:p6, :p7, :p8)
//                """.trimIndent(),
//                users.flatMapIndexed { i, user ->
//                    listOf(
//                        NamedSqlParameter.of("p${(i * 3)}", user.name),
//                        NamedSqlParameter.of("p${(i * 3 + 1)}", user.age),
//                        NamedSqlParameter.of("p${(i * 3) + 2}", user.address),
//                    )
//                },
//            ),
//        )
//    }
//
//    @Test
//    fun `select complex condition`() {
//        data class UserFilter(
//            val id: String,
//            val name: String?,
//            val age: Int?,
//            val address: String?,
//        )
//
//        val filter = UserFilter(id = "id", name = null, age = 18, address = null)
//        val sql = Sql.create {
//            +"SELECT *"
//            +"FROM user"
//            +"WHERE"
//            +"id = ${bind(filter.id)}"
//            filter.name?.let { +"AND name = ${bind(it)}" }
//            filter.age?.let { +"AND age = ${bind(it)}" }
//            filter.address?.let { +"AND address = ${bind(it)}" }
//        }
//        assertThat(sql).isEqualTo(
//            Sql.of(
//                """
//                SELECT *
//                FROM user
//                WHERE
//                id = :p0
//                AND age = :p1
//                """.trimIndent(),
//                listOf(
//                    NamedSqlParameter.of("p0", filter.id),
//                    NamedSqlParameter.of("p1", filter.age),
//                ),
//            ),
//        )
//    }
//
//    @Test
//    fun mixedOrder() {
//        val sql = Sql.create {
//            val line2 = "L2=${bind(2)}"
//            val line1 = "L1=${bind(1)}"
//            val line0 = "L0=${bind(0)}"
//
//            +line0
//            +line1
//            +line2
//        }
//        assertThat(sql).isEqualTo(
//            Sql.of(
//                """
//                L0=:p2
//                L1=:p1
//                L2=:p0
//                """.trimIndent(),
//                listOf(
//                    NamedSqlParameter.of("p0", 2),
//                    NamedSqlParameter.of("p1", 1),
//                    NamedSqlParameter.of("p2", 0),
//                ),
//            ),
//        )
//    }
}
