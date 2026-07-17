package dev.hsbrysk.kuery.core

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

/**
 * Behavior of the `autoTrimIndent` plugin option: every string passed to add/unaryPlus gets
 * `trimIndent()` applied exactly once — at compile time for templates and constants (proof that
 * the compile-time path fires is covered by `AutoTrimIndentBytecodeTest` in the compiler
 * module), at runtime for everything else. `addUnsafe` stays raw.
 */
@OptIn(DelicateKueryClientApi::class)
class AutoTrimIndentTest {
    @Test
    fun `multi-line template no longer needs an explicit trimIndent`() {
        val name = "n"
        val age = 18
        val sql = Sql {
            add(
                """
                UPDATE user
                SET name = $name, age = $age
                WHERE id = ${1}
                """,
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
    fun `unaryPlus template with values at line start and line end`() {
        val a = "A"
        val b = "B"
        val sql = Sql {
            +"""
            $a AND x
            y = $b
            """
        }
        assertThat(sql).isEqualTo(
            Sql(
                ":p0 AND x\ny = :p1",
                listOf(NamedSqlParameter("p0", a), NamedSqlParameter("p1", b)),
            ),
        )
    }

    @Test
    fun `blank line in the middle is preserved`() {
        val a = "A"
        val sql = Sql {
            +"""
            a = $a

            b
            """
        }
        assertThat(sql).isEqualTo(
            Sql(
                "a = :p0\n\nb",
                listOf(NamedSqlParameter("p0", a)),
            ),
        )
    }

    @Test
    fun `single-line leading whitespace is stripped`() {
        val sql = Sql {
            +"  SELECT 1"
        }
        assertThat(sql).isEqualTo(Sql("SELECT 1"))
    }

    @Test
    fun `constant string argument is trimmed`() {
        val sql = Sql {
            add(
                """
                SELECT *
                FROM user
                """,
            )
        }
        assertThat(sql).isEqualTo(Sql("SELECT *\nFROM user"))
    }

    @Suppress("KUERY_UNSAFE_SQL_STRING")
    @Test
    fun `variable argument is trimmed at runtime`() {
        val s = "\n    SELECT 1\n"
        val sql = Sql {
            add(s)
        }
        assertThat(sql).isEqualTo(Sql("SELECT 1"))
    }

    @Test
    fun `if expression argument is trimmed once as a whole`() {
        val x = "X"

        fun build(flag: Boolean) = Sql {
            +(
                if (flag) {
                    """
                    id = $x
                    AND y
                    """
                } else {
                    "  TRUE"
                }
                )
        }
        assertThat(build(true)).isEqualTo(
            Sql("id = :p0\nAND y", listOf(NamedSqlParameter("p0", x))),
        )
        assertThat(build(false)).isEqualTo(Sql("TRUE"))
    }

    @Suppress("KUERY_REDUNDANT_TRIM_INDENT")
    @Test
    fun `explicit trimIndent still works`() {
        val a = "A"
        val sql = Sql {
            +"""
            SELECT *
            WHERE id = $a
            """.trimIndent()
        }
        assertThat(sql).isEqualTo(
            Sql(
                "SELECT *\nWHERE id = :p0",
                listOf(NamedSqlParameter("p0", a)),
            ),
        )
    }

    @Test
    fun `explicit trimMargin still works`() {
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
    fun `post-margin indentation kept by an explicit trimMargin is also trimmed`() {
        // The automatic trim applies to whatever string reaches add — including the result of
        // an explicit trimMargin. Indentation deliberately kept after the margin prefix is
        // therefore stripped as well (documented in docs/basics.md; use addUnsafe to keep it).
        val sql = Sql {
            +"""
            |  SELECT *
            |  FROM user
            """.trimMargin()
        }
        assertThat(sql).isEqualTo(Sql("SELECT *\nFROM user"))
    }

    @Suppress("KUERY_REDUNDANT_TRIM_INDENT")
    @Test
    fun `blank edge line kept by an explicit trimIndent is also dropped`() {
        // Without the option the body would keep the blank line the explicit trimIndent
        // retained ("A\n\nSELECT 1"); the automatic trim drops it.
        val sql = Sql {
            +"A"
            +"""

            SELECT 1
            """.trimIndent()
        }
        assertThat(sql).isEqualTo(Sql("A\nSELECT 1"))
    }

    @Test
    fun `nested add inside an interpolation value`() {
        val x = "X"
        val sql = Sql {
            +"""
            A ${run {
                add("  B $x")
                "v"
            }}
            """
        }
        // The inner add runs first while the outer template's values are being evaluated, and
        // each add trims its own string.
        assertThat(sql).isEqualTo(
            Sql(
                "B :p0\nA :p1",
                listOf(NamedSqlParameter("p0", x), NamedSqlParameter("p1", "v")),
            ),
        )
    }

    @Test
    fun `string template inside an interpolation value stays a bound value`() {
        val sql = Sql {
            +"A ${listOf(1, 2).joinToString(", ") { "n $it" }}"
        }
        assertThat(sql).isEqualTo(
            Sql(
                "A :p0",
                listOf(NamedSqlParameter("p0", "n 1, n 2")),
            ),
        )
    }

    @Suppress("KUERY_UNSAFE_SQL_STRING")
    @Test
    fun `addUnsafe is not trimmed`() {
        val rawSql = """
            SELECT *
            FROM user
        """

        // The same raw string, side by side: add gets the automatic trimIndent ...
        val trimmed = Sql { add(rawSql) }
        assertThat(trimmed).isEqualTo(Sql("SELECT *\nFROM user"))

        // ... while addUnsafe — the escape hatch for keeping indentation — applies no
        // trimming at all, so the source indentation survives (Sql building itself only
        // ever trims the outer edges).
        val kept = Sql { addUnsafe(rawSql) }
        assertThat(kept).isEqualTo(Sql(rawSql.trim()))
    }
}
