package dev.hsbrysk.kuery.core

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.internal.DefaultSql
import org.junit.jupiter.api.Test

class SqlTest {
    @Test
    fun `Sql creates a DefaultSql with the given body and parameters`() {
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
    fun `Sql is not affected by later mutation of the caller parameter list`() {
        // given
        val parameters = mutableListOf(NamedSqlParameter("p0", 1))
        val sql = Sql("SELECT * FROM some_table WHERE id = :p0", parameters)

        // when
        parameters.add(NamedSqlParameter("p1", 2))

        // then
        assertThat(sql.parameters).isEqualTo(listOf(NamedSqlParameter("p0", 1)))
    }
}
