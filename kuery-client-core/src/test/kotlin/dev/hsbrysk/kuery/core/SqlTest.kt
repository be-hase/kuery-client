package dev.hsbrysk.kuery.core

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.internal.DefaultSql
import org.junit.jupiter.api.Test

class SqlTest {
    @Test
    fun of() {
        assertThat(
            Sql(
                "SELECT * FROM some_table",
                listOf(NamedSqlParameter("hoge", "hoge-value")),
            ),
        )
            .isEqualTo(
                DefaultSql(
                    "SELECT * FROM some_table",
                    listOf(NamedSqlParameter("hoge", "hoge-value")),
                ),
            )
    }

    @Test
    fun notAffectedByCallerListMutation() {
        val parameters = mutableListOf(NamedSqlParameter("p0", 1))
        val sql = Sql("SELECT * FROM some_table WHERE id = :p0", parameters)

        parameters.add(NamedSqlParameter("p1", 2))

        assertThat(sql.parameters).isEqualTo(listOf(NamedSqlParameter("p0", 1)))
    }
}
