package dev.hsbrysk.kuery.core

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

/**
 * Behavioral equivalence of the compile-time trimIndent/trimMargin folding: whether a case is
 * folded by the plugin or falls back to runtime trimming, the resulting [Sql] must be identical
 * to what runtime trimming has always produced. Proof that folding actually fires is covered by
 * `TrimFoldingBytecodeTest` in the compiler module.
 */
class TrimFoldingTest {
    @Test
    fun `trimIndent with values mid-line`() {
        val name = "n"
        val age = 18
        val sql = Sql {
            add(
                """
                UPDATE user
                SET name = $name, age = $age
                WHERE id = ${1}
                """.trimIndent(),
            )
        }
        assertThat(sql).isEqualTo(
            Sql(
                "UPDATE user\nSET name = :p0, age = :p1\nWHERE id = :p2",
                listOf(
                    NamedSqlParameter("p0", name),
                    NamedSqlParameter("p1", age),
                    NamedSqlParameter("p2", 1),
                ),
            ),
        )
    }

    @Test
    fun `trimIndent with values at line start and line end`() {
        val a = "A"
        val b = "B"
        val sql = Sql {
            +"""
            $a AND x
            y = $b
            """.trimIndent()
        }
        assertThat(sql).isEqualTo(
            Sql(
                ":p0 AND x\ny = :p1",
                listOf(NamedSqlParameter("p0", a), NamedSqlParameter("p1", b)),
            ),
        )
    }

    @Test
    fun `trimIndent with a blank line in the middle`() {
        val a = "A"
        val sql = Sql {
            +"""
            a = $a

            b
            """.trimIndent()
        }
        assertThat(sql).isEqualTo(
            Sql(
                "a = :p0\n\nb",
                listOf(NamedSqlParameter("p0", a)),
            ),
        )
    }

    @Test
    fun `trimIndent with consecutive values`() {
        val a = "A"
        val b = "B"
        val sql = Sql {
            +"""
            a
            $a$b c
            """.trimIndent()
        }
        assertThat(sql).isEqualTo(
            Sql(
                "a\n:p0:p1 c",
                listOf(NamedSqlParameter("p0", a), NamedSqlParameter("p1", b)),
            ),
        )
    }

    @Test
    fun `trimIndent on a constant string`() {
        val sql = Sql {
            add(
                """
                SELECT *
                FROM user
                """.trimIndent(),
            )
        }
        assertThat(sql).isEqualTo(Sql("SELECT *\nFROM user"))
    }

    @Test
    fun `trimMargin with the default prefix`() {
        val a = "A"
        val sql = Sql {
            +"""
            |SELECT *
            |WHERE id = $a
            """.trimMargin()
        }
        assertThat(sql).isEqualTo(
            Sql(
                "SELECT *\nWHERE id = :p0",
                listOf(NamedSqlParameter("p0", a)),
            ),
        )
    }

    @Test
    fun `trimMargin with a literal prefix`() {
        val a = "A"
        val sql = Sql {
            +"""
            > SELECT *
            > WHERE id = $a
            """.trimMargin("> ")
        }
        assertThat(sql).isEqualTo(
            Sql(
                "SELECT *\nWHERE id = :p0",
                listOf(NamedSqlParameter("p0", a)),
            ),
        )
    }

    @Test
    fun `trimMargin with a colon prefix falls back to runtime trimming`() {
        // A ':' prefix could match into the runtime ':pN' placeholders, so the plugin must not
        // fold it. The runtime behavior (margin stripped from the line below) is preserved.
        val a = "A"
        val sql = Sql {
            +"""
            :x = $a
            """.trimMargin(":")
        }
        assertThat(sql).isEqualTo(
            Sql(
                "x = :p0",
                listOf(NamedSqlParameter("p0", a)),
            ),
        )
    }

    @Suppress("KUERY_UNSAFE_SQL_STRING")
    @Test
    fun `trimIndent on a variable falls back to runtime trimming`() {
        val s = "\n    SELECT 1\n"
        val sql = Sql {
            add(s.trimIndent())
        }
        assertThat(sql).isEqualTo(Sql("SELECT 1"))
    }

    @Test
    fun `trimIndent inside a when branch`() {
        val a = "A"

        fun build(flag: Boolean) = Sql {
            add(
                when (flag) {
                    true ->
                        """
                        SELECT *
                        WHERE id = $a
                        """.trimIndent()
                    false -> "SELECT 1"
                },
            )
        }
        assertThat(build(true)).isEqualTo(
            Sql(
                "SELECT *\nWHERE id = :p0",
                listOf(NamedSqlParameter("p0", a)),
            ),
        )
        assertThat(build(false)).isEqualTo(Sql("SELECT 1"))
    }

    @Test
    fun `nested add inside an interpolation value of a trimmed template`() {
        val x = "X"
        val sql = Sql {
            +"""
            A ${run {
                add("B $x")
                "v"
            }}
            """.trimIndent()
        }
        // The inner add runs first while the outer template's values are being evaluated,
        // then the outer (folded) template binds the run block's result as a single value.
        assertThat(sql).isEqualTo(
            Sql(
                "B :p0\nA :p1",
                listOf(NamedSqlParameter("p0", x), NamedSqlParameter("p1", "v")),
            ),
        )
    }

    @Test
    fun `literal trimIndent inside an interpolation value stays a bound value`() {
        val x = "X"
        val sql = Sql {
            +"a ${" x".trimIndent()} b $x"
        }
        // The trim call belongs to the value, so it must not be folded into SQL text.
        assertThat(sql).isEqualTo(
            Sql(
                "a :p0 b :p1",
                listOf(NamedSqlParameter("p0", "x"), NamedSqlParameter("p1", x)),
            ),
        )
    }
}
