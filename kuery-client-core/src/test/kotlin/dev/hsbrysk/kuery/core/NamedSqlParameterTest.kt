package dev.hsbrysk.kuery.core

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.internal.DefaultNamedSqlParameter
import org.junit.jupiter.api.Test

class NamedSqlParameterTest {
    @Test
    fun of() {
        assertThat(NamedSqlParameter.of("hoge", "hoge-value", String::class))
            .isEqualTo(DefaultNamedSqlParameter("hoge", "hoge-value", String::class))

        assertThat(NamedSqlParameter.of("hoge", "hoge-value"))
            .isEqualTo(DefaultNamedSqlParameter("hoge", "hoge-value", String::class))
    }
}
