package dev.hsbrysk.kuery.core

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import dev.hsbrysk.kuery.core.Sql as BuildSql

// Lives in its own file because the alias import shadows the plain `Sql` name file-wide.
class AliasedSqlEntryPointTest {
    @Test
    fun `the transformation applies to the Sql entry point imported under an alias`() {
        // The IR rewrite matches add/unaryPlus by their resolved declarations, so how the entry
        // point is imported must not matter: interpolation is still converted into a bind.
        // given
        val id = 1

        // when
        val sql = BuildSql {
            +"SELECT * FROM users WHERE id = $id"
        }

        // then
        assertThat(sql).isEqualTo(
            BuildSql(
                "SELECT * FROM users WHERE id = :p0",
                listOf(NamedSqlParameter("p0", 1)),
            ),
        )
    }
}
