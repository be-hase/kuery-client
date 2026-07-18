package dev.hsbrysk.kuery.core

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import io.mockk.InternalPlatformDsl.toStr
import io.mockk.mockk
import org.junit.jupiter.api.Test

private const val TABLE = "users"
private const val ORDER_COLUMN = "created_at"
private const val LIMIT = 100

class StringInterpolationTest {
    @Test
    fun `an empty block builds an empty Sql`() {
        val sql = Sql {
        }
        assertThat(sql).isEqualTo(Sql(""))
    }

    @Test
    fun `empty fragments collapse to an empty Sql`() {
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
    fun `a template consisting only of values produces only placeholders`() {
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
    fun `fragments without values are joined with newlines`() {
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
    fun `values become placeholders at their exact positions in the text`() {
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
    fun `int constants and int expressions are bound as parameters`() {
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
    fun `an int const val is bound as a parameter`() {
        val sql = Sql {
            +"LIMIT $LIMIT"
        }
        assertThat(sql).isEqualTo(
            Sql(
                "LIMIT :p0",
                listOf(NamedSqlParameter("p0", 100)),
            ),
        )
    }

    @Test
    fun `a string literal value is inlined as raw text`() {
        // A string literal is a compile-time constant, so it is not bound as a parameter.
        val sql = Sql {
            +"a ${"hoge"}"
        }
        assertThat(sql).isEqualTo(
            Sql(
                "a hoge",
            ),
        )
    }

    @Test
    fun `a computed string value is bound as a parameter`() {
        val sql = Sql {
            +"a ${"hoge".removePrefix("h").removePrefix("o")}"
        }
        assertThat(sql).isEqualTo(
            Sql(
                "a :p0",
                listOf(NamedSqlParameter("p0", "ge")),
            ),
        )
    }

    @Test
    fun `boolean constants and boolean expressions are bound as parameters`() {
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
    fun `an add nested in a run block emits its line before the outer add`() {
        // given
        val x = "X"
        val y = "1; DROP TABLE users"

        // when
        val sql = Sql {
            add(
                run {
                    add("B $x")
                    "S $y"
                },
            )
        }

        // then
        assertThat(sql).isEqualTo(
            Sql(
                "B :p0\nS :p1",
                listOf(NamedSqlParameter("p0", "X"), NamedSqlParameter("p1", y)),
            ),
        )
    }

    @Suppress("KUERY_UNSAFE_SQL_STRING")
    @Test
    fun `a unaryPlus nested in a run block emits its line before the outer one`() {
        // given
        val x = "X"
        val y = "1; DROP TABLE users"

        // when
        val sql = Sql {
            +run {
                +"B $x"
                "S $y"
            }
        }

        // then
        assertThat(sql).isEqualTo(
            Sql(
                "B :p0\nS :p1",
                listOf(NamedSqlParameter("p0", "X"), NamedSqlParameter("p1", y)),
            ),
        )
    }

    @Suppress("KUERY_UNSAFE_SQL_STRING")
    @Test
    fun `doubly nested adds emit lines in innermost-first order`() {
        // given
        val x = "X"
        val y = "Y"
        val z = "Z"

        // when
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

        // then
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
    fun `an add inside an interpolation value emits first and the block result is bound`() {
        // given
        val x = "X"
        val y = "Y"

        // when
        val sql = Sql {
            +"A ${run {
                add("B $x")
                "v"
            }} C $y"
        }

        // then
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
    fun `a unaryPlus inside an interpolation value emits first and the block result is bound`() {
        // given
        val x = "X"

        // when
        val sql = Sql {
            +"A ${run {
                +"B $x"
                "v"
            }}"
        }

        // then
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
    fun `templates inside if branches are converted into bind parameters`() {
        // The IR transformation keeps its context across the whole argument expression,
        // so templates inside if branches are converted into bind parameters.
        // This is the premise for KUERY_UNSAFE_SQL_STRING accepting if/when expressions
        // with safe branches (allWarningsAsErrors guards that no warning is reported).

        // given
        val x = "X"

        fun build(flag: Boolean) = Sql {
            +(if (flag) "id = $x" else "TRUE")
        }

        // when & then
        assertThat(build(true)).isEqualTo(
            Sql("id = :p0", listOf(NamedSqlParameter("p0", "X"))),
        )
        assertThat(build(false)).isEqualTo(Sql("TRUE"))
    }

    @Test
    fun `templates inside when branches are converted and const branches stay raw text`() {
        // given
        val x = "X"

        fun build(sort: String?) = Sql {
            add(
                when (sort) {
                    "name" -> "ORDER BY name"
                    "id" -> "ORDER BY id, $x"
                    else -> ORDER_COLUMN
                },
            )
        }

        // when & then
        assertThat(build("name")).isEqualTo(Sql("ORDER BY name"))
        assertThat(build("id")).isEqualTo(
            Sql("ORDER BY id, :p0", listOf(NamedSqlParameter("p0", "X"))),
        )
        // A const val branch is a compile-time constant, expanded as raw SQL text.
        assertThat(build(null)).isEqualTo(Sql("created_at"))
    }

    @Test
    fun `a const val in a template is expanded as raw SQL text`() {
        // given
        val id = 1

        // when
        val sql = Sql {
            +"SELECT * FROM $TABLE WHERE id = $id"
        }

        // then
        assertThat(sql).isEqualTo(
            Sql(
                "SELECT * FROM users WHERE id = :p0",
                listOf(NamedSqlParameter("p0", 1)),
            ),
        )
    }

    @Test
    fun `a template consisting of a single const val expands to its raw text`() {
        val sql = Sql {
            +"$TABLE"
        }
        assertThat(sql).isEqualTo(Sql("users"))
    }

    @Test
    fun `a Java static final constant in a template is expanded as raw SQL text`() {
        // FIR2IR folds Java compile-time constants exactly like Kotlin const vals: the value is
        // spliced as SQL text, not bound as a parameter.
        val sql = Sql {
            +"SELECT * FROM ${javax.xml.XMLConstants.XML_NS_PREFIX}_users"
        }
        assertThat(sql).isEqualTo(Sql("SELECT * FROM xml_users"))
    }

    @Test
    fun `a const val passed directly to add executes as raw SQL text`() {
        // A const val reference is a compile-time constant, so it is accepted without
        // a KUERY_UNSAFE_SQL_STRING warning and executed as raw SQL text.
        val sql = Sql {
            add(TABLE)
        }
        assertThat(sql).isEqualTo(Sql("users"))
    }

    @Test
    fun `a trailing const val expands as raw text while runtime values are bound`() {
        // given
        val id = 1

        // when
        val sql = Sql {
            +"WHERE id = $id ORDER BY $ORDER_COLUMN"
        }

        // then
        assertThat(sql).isEqualTo(
            Sql(
                "WHERE id = :p0 ORDER BY created_at",
                listOf(NamedSqlParameter("p0", 1)),
            ),
        )
    }

    @Test
    fun `an interpolated char constant is inlined as raw text`() {
        // given
        val x = 1

        // when
        val sql = Sql {
            +"SELECT data->>'${'$'}name' FROM t WHERE id = $x"
        }

        // then
        assertThat(sql).isEqualTo(
            Sql(
                "SELECT data->>'\$name' FROM t WHERE id = :p0",
                listOf(NamedSqlParameter("p0", 1)),
            ),
        )
    }

    @Test
    fun `interpolated percent chars are inlined as raw text around a bound value`() {
        // given
        val keyword = "foo"

        // when
        val sql = Sql {
            +"WHERE name LIKE ${'%'}$keyword${'%'}"
        }

        // then
        assertThat(sql).isEqualTo(
            Sql(
                "WHERE name LIKE %:p0%",
                listOf(NamedSqlParameter("p0", "foo")),
            ),
        )
    }

    @Test
    fun `consecutive constants are merged`() {
        // given
        val x = 1

        // when
        val sql = Sql {
            +"a$TABLE${'c'} $x"
        }

        // then
        assertThat(sql).isEqualTo(
            Sql(
                "ausersc :p0",
                listOf(NamedSqlParameter("p0", 1)),
            ),
        )
    }

    @Test
    fun `an inlined literal and a bound runtime value coexist in one template`() {
        // given
        val x = 1

        // when
        val sql = Sql {
            +"a ${"hoge"} b $x"
        }

        // then
        assertThat(sql).isEqualTo(
            Sql(
                "a hoge b :p0",
                listOf(NamedSqlParameter("p0", 1)),
            ),
        )
    }

    @Test
    fun `receiver expression is evaluated only once`() {
        // given
        var count = 0

        // when
        val sql = Sql {
            fun receiver(): SqlBuilder {
                count++
                return this
            }
            receiver().add("WHERE id = ${1}")
        }

        // then
        assertThat(sql).isEqualTo(
            Sql("WHERE id = :p0", listOf(NamedSqlParameter("p0", 1))),
        )
        assertThat(count).isEqualTo(1)
    }

    @Test
    fun `body and parameter go to the same builder even if the receiver expression is impure`() {
        // given
        var callCount = 0
        lateinit var outerBuilder: SqlBuilder
        val innerSqls = mutableListOf<Sql>()

        // when
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

        // then
        // The receiver expression must be evaluated once, so both the SQL body and
        // the parameter must go to its first result (= outerBuilder).
        assertThat(outerSql).isEqualTo(
            Sql("WHERE id = :p0", listOf(NamedSqlParameter("p0", 42))),
        )
        assertThat(innerSqls.first()).isEqualTo(Sql(""))
    }

    @Test
    fun `a null constant is bound as a null parameter`() {
        val sql = Sql {
            +"a ${null}"
        }
        assertThat(sql).isEqualTo(
            Sql(
                "a :p0",
                listOf(NamedSqlParameter("p0", null)),
            ),
        )
    }

    @Test
    fun `a computed null string is bound as the string null`() {
        // The expression evaluates to the string "null", not to a null reference.
        val sql = Sql {
            +"a ${null.toStr()}"
        }
        assertThat(sql).isEqualTo(
            Sql(
                "a :p0",
                listOf(NamedSqlParameter("p0", "null")),
            ),
        )
    }

    @Test
    fun `the transformation applies inside a SqlBuilder lambda passed to a custom sql overload`() {
        // The IR rewrite matches add/unaryPlus by their resolved declarations, independent of
        // the function the lambda is passed to — and a subtype's own `sql` overload is not an
        // override of the client's, so the sqlId injection must leave the call untouched.
        // given
        class Decorated(delegate: KueryBlockingClient) : KueryBlockingClient by delegate {
            fun sql(
                block: SqlBuilder.() -> Unit,
                after: () -> Unit,
            ): Sql {
                after()
                return Sql(block)
            }
        }

        val decorated = Decorated(mockk())
        val id = 1
        var afterRan = false

        // when
        val sql = decorated.sql(
            { +"SELECT * FROM users WHERE id = $id" },
            { afterRan = true },
        )

        // then
        assertThat(sql).isEqualTo(
            Sql(
                "SELECT * FROM users WHERE id = :p0",
                listOf(NamedSqlParameter("p0", 1)),
            ),
        )
        assertThat(afterRan).isTrue()
    }
}
