package dev.hsbrysk.kuery.core

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.InternalPlatformDsl.toStr
import org.junit.jupiter.api.Test

private const val TABLE = "users"
private const val ORDER_COLUMN = "created_at"

class StringInterpolationTest {
    @Test
    fun none() {
        val sql = Sql {
        }
        assertThat(sql).isEqualTo(Sql(""))
    }

    @Test
    fun `empty string`() {
        val sql1 = Sql {
            +""
        }
        assertThat(sql1).isEqualTo(Sql(""))

        // Unnecessary line breaks are trimmed
        // see DefaultSqlBuilder.build()
        val sql2 = Sql {
            +""
            +""
            +""
        }
        assertThat(sql2).isEqualTo(Sql(""))
    }

    @Test
    fun `only string interpolation`() {
        val sql1 = Sql {
            +"${1}"
        }
        assertThat(sql1).isEqualTo(
            Sql(
                ":p0",
                listOf(NamedSqlParameter("p0", 1)),
            ),
        )

        val sql2 = Sql {
            +"${1}${2}"
        }
        assertThat(sql2).isEqualTo(
            Sql(
                ":p0:p1",
                listOf(NamedSqlParameter("p0", 1), NamedSqlParameter("p1", 2)),
            ),
        )
    }

    @Test
    fun `only fragments`() {
        val sql1 = Sql {
            +"hoge"
        }
        assertThat(sql1).isEqualTo(
            Sql("hoge"),
        )

        val sql2 = Sql {
            +"h"
            +"o"
            +"g"
            +"e"
        }
        assertThat(sql2).isEqualTo(
            Sql("h\no\ng\ne"),
        )
    }

    @Test
    fun mixed() {
        val sql1 = Sql {
            +"a${1}b"
        }
        assertThat(sql1).isEqualTo(
            Sql("a:p0b", listOf(NamedSqlParameter("p0", 1))),
        )

        val sql2 = Sql {
            +"${1}a"
        }
        assertThat(sql2).isEqualTo(
            Sql(":p0a", listOf(NamedSqlParameter("p0", 1))),
        )

        val sql3 = Sql {
            +"a${1}"
        }
        assertThat(sql3).isEqualTo(
            Sql("a:p0", listOf(NamedSqlParameter("p0", 1))),
        )

        val sql4 = Sql {
            +"a${1}${2}${3}"
        }
        assertThat(sql4).isEqualTo(
            Sql(
                "a:p0:p1:p2",
                listOf(NamedSqlParameter("p0", 1), NamedSqlParameter("p1", 2), NamedSqlParameter("p2", 3)),
            ),
        )

        val sql5 = Sql {
            +"a${1}"
            +"b${2}"
        }
        assertThat(sql5).isEqualTo(
            Sql("a:p0\nb:p1", listOf(NamedSqlParameter("p0", 1), NamedSqlParameter("p1", 2))),
        )
    }

    @Test
    fun `int string interpolation`() {
        val sql1 = Sql {
            +"a ${1}"
        }
        assertThat(sql1).isEqualTo(
            Sql(
                "a :p0",
                listOf(NamedSqlParameter("p0", 1)),
            ),
        )

        val sql2 = Sql {
            +"a ${1 + 1}"
        }
        assertThat(sql2).isEqualTo(
            Sql(
                "a :p0",
                listOf(NamedSqlParameter("p0", 2)),
            ),
        )
    }

    @Test
    fun `string string interpolation`() {
        // In such cases, string interpolation will not be executed.
        val sql1 = Sql {
            +"a ${"hoge"}"
        }
        assertThat(sql1).isEqualTo(
            Sql(
                "a hoge",
            ),
        )

        // On the other hand, in such cases, it will be executed.
        val sql2 = Sql {
            +"a ${"hoge".removePrefix("h").removePrefix("o")}"
        }
        assertThat(sql2).isEqualTo(
            Sql(
                "a :p0",
                listOf(NamedSqlParameter("p0", "ge")),
            ),
        )
    }

    @Test
    fun `boolean string interpolation`() {
        // In such cases, string interpolation will not be executed.
        val sql1 = Sql {
            +"a ${true}"
        }
        assertThat(sql1).isEqualTo(
            Sql(
                "a :p0",
                listOf(NamedSqlParameter("p0", true)),
            ),
        )

        // On the other hand, in such cases, it will be executed.
        val sql2 = Sql {
            +"a ${true && true}"
        }
        assertThat(sql2).isEqualTo(
            Sql(
                "a :p0",
                listOf(NamedSqlParameter("p0", true)),
            ),
        )
    }

    @Test
    fun `nested add`() {
        val x = "X"
        val y = "1; DROP TABLE users"
        val sql = Sql {
            add(
                run {
                    add("B $x")
                    "S $y"
                },
            )
        }
        assertThat(sql).isEqualTo(
            Sql(
                "B :p0\nS :p1",
                listOf(NamedSqlParameter("p0", "X"), NamedSqlParameter("p1", y)),
            ),
        )
    }

    @Test
    fun `nested unaryPlus`() {
        val x = "X"
        val y = "1; DROP TABLE users"
        val sql = Sql {
            +run {
                +"B $x"
                "S $y"
            }
        }
        assertThat(sql).isEqualTo(
            Sql(
                "B :p0\nS :p1",
                listOf(NamedSqlParameter("p0", "X"), NamedSqlParameter("p1", y)),
            ),
        )
    }

    @Test
    fun `doubly nested add`() {
        val x = "X"
        val y = "Y"
        val z = "Z"
        val sql = Sql {
            add(
                run {
                    add(
                        run {
                            add("A $x")
                            "B $y"
                        },
                    )
                    "C $z"
                },
            )
        }
        assertThat(sql).isEqualTo(
            Sql(
                "A :p0\nB :p1\nC :p2",
                listOf(
                    NamedSqlParameter("p0", "X"),
                    NamedSqlParameter("p1", "Y"),
                    NamedSqlParameter("p2", "Z"),
                ),
            ),
        )
    }

    @Test
    fun `const val string interpolation`() {
        val id = 1
        val sql = Sql {
            +"SELECT * FROM $TABLE WHERE id = $id"
        }
        assertThat(sql).isEqualTo(
            Sql(
                "SELECT * FROM users WHERE id = :p0",
                listOf(NamedSqlParameter("p0", 1)),
            ),
        )
    }

    @Test
    fun `const val only`() {
        val sql = Sql {
            +"$TABLE"
        }
        assertThat(sql).isEqualTo(Sql("users"))
    }

    @Test
    fun `trailing const val`() {
        val id = 1
        val sql = Sql {
            +"WHERE id = $id ORDER BY $ORDER_COLUMN"
        }
        assertThat(sql).isEqualTo(
            Sql(
                "WHERE id = :p0 ORDER BY created_at",
                listOf(NamedSqlParameter("p0", 1)),
            ),
        )
    }

    @Test
    fun `char constant string interpolation`() {
        val x = 1
        val sql = Sql {
            +"SELECT data->>'${'$'}name' FROM t WHERE id = $x"
        }
        assertThat(sql).isEqualTo(
            Sql(
                "SELECT data->>'\$name' FROM t WHERE id = :p0",
                listOf(NamedSqlParameter("p0", 1)),
            ),
        )
    }

    @Test
    fun `char percent constant string interpolation`() {
        val keyword = "foo"
        val sql = Sql {
            +"WHERE name LIKE ${'%'}$keyword${'%'}"
        }
        assertThat(sql).isEqualTo(
            Sql(
                "WHERE name LIKE %:p0%",
                listOf(NamedSqlParameter("p0", "foo")),
            ),
        )
    }

    @Test
    fun `consecutive constants are merged`() {
        val x = 1
        val sql = Sql {
            +"a$TABLE${'c'} $x"
        }
        assertThat(sql).isEqualTo(
            Sql(
                "ausersc :p0",
                listOf(NamedSqlParameter("p0", 1)),
            ),
        )
    }

    @Test
    fun `literal string interpolation mixed with runtime value`() {
        val x = 1
        val sql = Sql {
            +"a ${"hoge"} b $x"
        }
        assertThat(sql).isEqualTo(
            Sql(
                "a hoge b :p0",
                listOf(NamedSqlParameter("p0", 1)),
            ),
        )
    }

    @Test
    fun `null string interpolation`() {
        // In such cases, string interpolation will not be executed.
        val sql1 = Sql {
            +"a ${null}"
        }
        assertThat(sql1).isEqualTo(
            Sql(
                "a :p0",
                listOf(NamedSqlParameter("p0", null)),
            ),
        )

        // On the other hand, in such cases, it will be executed.
        val sql2 = Sql {
            +"a ${null.toStr()}"
        }
        assertThat(sql2).isEqualTo(
            Sql(
                "a :p0",
                listOf(NamedSqlParameter("p0", "null")),
            ),
        )
    }
}
