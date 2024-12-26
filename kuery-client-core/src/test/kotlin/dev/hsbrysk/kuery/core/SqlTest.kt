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
}
