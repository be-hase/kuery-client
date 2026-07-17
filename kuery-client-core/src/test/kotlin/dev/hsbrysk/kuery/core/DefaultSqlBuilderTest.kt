package dev.hsbrysk.kuery.core

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.message
import org.junit.jupiter.api.Test

class DefaultSqlBuilderTest {
    @Test
    fun `add throws IllegalStateException when the compiler plugin is not applied`() {
        // when & then
        assertFailure {
            DefaultSqlBuilder().add("")
        }.isInstanceOf(IllegalStateException::class)
            .message().isNotNull().all {
                contains("dev.hsbrysk.kuery-client")
                contains("https://kuery-client.hsbrysk.dev/")
            }
    }

    @Test
    fun `unaryPlus throws IllegalStateException when the compiler plugin is not applied`() {
        // when & then
        assertFailure {
            with(DefaultSqlBuilder()) {
                +""
            }
        }.isInstanceOf(IllegalStateException::class)
            .message().isNotNull().all {
                contains("dev.hsbrysk.kuery-client")
                contains("https://kuery-client.hsbrysk.dev/")
            }
    }

    @Test
    fun `addUnsafe joins fragments with newlines, ignoring empty fragments`() {
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
    fun `bind returns sequential placeholders and records the parameters in order`() {
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
    fun `binding the same value twice creates two parameters`() {
        DefaultSqlBuilder()
            .apply {
                assertThat(bind(1)).isEqualTo(":p0")
                assertThat(bind(1)).isEqualTo(":p1")
            }
            .build()
            .let {
                assertThat(it).isEqualTo(Sql("", listOf(NamedSqlParameter("p0", 1), NamedSqlParameter("p1", 1))))
            }
    }

    @Test
    fun `bind binds a collection as a single parameter holding the collection itself`() {
        // A collection is bound as a single parameter holding the collection itself.
        val ids = listOf(1, 2, 3)
        DefaultSqlBuilder()
            .apply {
                assertThat(bind(ids)).isEqualTo(":p0")
            }
            .build()
            .let {
                assertThat(it).isEqualTo(Sql("", listOf(NamedSqlParameter("p0", ids))))
            }
    }

    @Test
    fun `build returns a snapshot that is not affected by later bind calls`() {
        // given
        val builder = DefaultSqlBuilder().apply {
            addUnsafe("SELECT * FROM some_table WHERE id = ${bind(1)}")
        }
        val sql = builder.build()

        // when
        builder.bind(2)

        // then
        assertThat(sql.parameters).isEqualTo(listOf(NamedSqlParameter("p0", 1)))
    }

    @Test
    fun `interpolate weaves placeholders between fragments and throws IllegalStateException on count mismatch`() {
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
