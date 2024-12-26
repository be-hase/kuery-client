package dev.hsbrysk.kuery.core

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.InternalPlatformDsl.toStr
import org.junit.jupiter.api.Test

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
