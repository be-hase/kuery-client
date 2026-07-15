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

    @Suppress("KUERY_UNSAFE_SQL_STRING")
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

    @Suppress("KUERY_UNSAFE_SQL_STRING")
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

    @Suppress("KUERY_UNSAFE_SQL_STRING")
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
    fun `nested add inside an interpolation value`() {
        val x = "X"
        val y = "Y"
        val sql = Sql {
            +"A ${run {
                add("B $x")
                "v"
            }} C $y"
        }
        // The inner add runs first while the outer template's values are being evaluated,
        // then the outer template binds the run block's result ("v") as a single value.
        assertThat(sql).isEqualTo(
            Sql(
                "B :p0\nA :p1 C :p2",
                listOf(
                    NamedSqlParameter("p0", "X"),
                    NamedSqlParameter("p1", "v"),
                    NamedSqlParameter("p2", "Y"),
                ),
            ),
        )
    }

    @Test
    fun `nested unaryPlus inside an interpolation value`() {
        val x = "X"
        val sql = Sql {
            +"A ${run {
                +"B $x"
                "v"
            }}"
        }
        assertThat(sql).isEqualTo(
            Sql(
                "B :p0\nA :p1",
                listOf(
                    NamedSqlParameter("p0", "X"),
                    NamedSqlParameter("p1", "v"),
                ),
            ),
        )
    }

    @Test
    fun `string template inside an interpolation value stays a plain concatenation`() {
        val sql = Sql {
            +"A ${listOf(1, 2).joinToString(", ") { "n $it" }}"
        }
        // The template inside the lambda belongs to the value, not to the SQL text,
        // so the whole evaluated result is bound as a single parameter.
        assertThat(sql).isEqualTo(
            Sql(
                "A :p0",
                listOf(NamedSqlParameter("p0", "n 1, n 2")),
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
    fun `const val as whole argument`() {
        // A const val reference is a compile-time constant, so it is accepted without
        // a KUERY_UNSAFE_SQL_STRING warning and executed as raw SQL text.
        val sql = Sql {
            add(TABLE)
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
    fun `receiver expression is evaluated only once`() {
        var count = 0
        val sql = Sql {
            fun receiver(): SqlBuilder {
                count++
                return this
            }
            receiver().add("WHERE id = ${1}")
        }
        assertThat(sql).isEqualTo(
            Sql("WHERE id = :p0", listOf(NamedSqlParameter("p0", 1))),
        )
        assertThat(count).isEqualTo(1)
    }

    @Test
    fun `body and parameter go to the same builder even if the receiver expression is impure`() {
        var callCount = 0
        lateinit var outerBuilder: SqlBuilder
        val innerSqls = mutableListOf<Sql>()
        val outerSql = Sql {
            outerBuilder = this
            val innerSql = Sql {
                val innerBuilder = this
                fun receiver(): SqlBuilder {
                    callCount++
                    // Returns a different builder on each call
                    return if (callCount == 1) outerBuilder else innerBuilder
                }
                receiver().add("WHERE id = ${42}")
            }
            innerSqls.add(innerSql)
        }
        // The receiver expression must be evaluated once, so both the SQL body and
        // the parameter must go to its first result (= outerBuilder).
        assertThat(outerSql).isEqualTo(
            Sql("WHERE id = :p0", listOf(NamedSqlParameter("p0", 42))),
        )
        assertThat(innerSqls.first()).isEqualTo(Sql(""))
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
