package dev.hsbrysk.kuery.core.internal

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import dev.hsbrysk.kuery.core.NamedSqlParameter
import dev.hsbrysk.kuery.core.Sql
import org.junit.jupiter.api.Test

class DefaultSqlBuilderTest {
    @Test
    fun add() {
        assertFailure {
            DefaultSqlBuilder().add("")
        }.isInstanceOf(IllegalStateException::class)
    }

    @Test
    fun unaryPlus() {
        assertFailure {
            with(DefaultSqlBuilder()) {
                +""
            }
        }.isInstanceOf(IllegalStateException::class)
    }

    @Test
    fun addUnsafe() {
        DefaultSqlBuilder()
            .apply {
                addUnsafe("")
            }
            .build()
            .let {
                assertThat(it).isEqualTo(Sql(""))
            }
        DefaultSqlBuilder()
            .apply {
                addUnsafe("")
                addUnsafe("")
            }
            .build()
            .let {
                assertThat(it).isEqualTo(Sql(""))
            }
        DefaultSqlBuilder()
            .apply {
                addUnsafe("hoge")
            }
            .build()
            .let {
                assertThat(it).isEqualTo(Sql("hoge"))
            }
        DefaultSqlBuilder()
            .apply {
                addUnsafe("hoge")
                addUnsafe("bar")
            }
            .build()
            .let {
                assertThat(it).isEqualTo(Sql("hoge\nbar"))
            }
    }

    @Test
    fun bind() {
        DefaultSqlBuilder()
            .apply {
                assertThat(bind(1)).isEqualTo(":p0")
            }
            .build()
            .let {
                assertThat(it).isEqualTo(Sql("", listOf(NamedSqlParameter("p0", 1))))
            }
        DefaultSqlBuilder()
            .apply {
                assertThat(bind(1)).isEqualTo(":p0")
                assertThat(bind(2)).isEqualTo(":p1")
            }
            .build()
            .let {
                assertThat(it).isEqualTo(Sql("", listOf(NamedSqlParameter("p0", 1), NamedSqlParameter("p1", 2))))
            }
    }

    @Test
    fun interpolate() {
        assertThat(DefaultSqlBuilder().interpolate(emptyList(), emptyList())).isEqualTo("")
        assertThat(DefaultSqlBuilder().interpolate(listOf("a"), emptyList())).isEqualTo("a")
        assertThat(DefaultSqlBuilder().interpolate(listOf("a"), listOf(1))).isEqualTo("a:p0")
        assertThat(DefaultSqlBuilder().interpolate(listOf("a", "b"), listOf(1))).isEqualTo("a:p0b")
        assertFailure {
            DefaultSqlBuilder().interpolate(listOf("a", "b"), emptyList())
        }.isInstanceOf(IllegalStateException::class)
        assertFailure {
            DefaultSqlBuilder().interpolate(listOf("a"), listOf(1, 2))
        }.isInstanceOf(IllegalStateException::class)
    }
}
