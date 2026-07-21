package dev.hsbrysk.kuery.spring.testing.contract.conversion

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.DelicateKueryClientApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter

/**
 * Contract shared by the jdbc and r2dbc modules. Each module runs it via a concrete subclass
 * that supplies its `ContractDatabase`.
 *
 * A null element in an IN list behaves differently per module (JDBC binds SQL NULL and never
 * matches; R2DBC's Statement.bind rejects null outright), so that case is a module-specific test
 * on each concrete subclass rather than part of this contract.
 */
abstract class CollectionConversionContract : ConverterContractBase() {
    override val converters: List<Any>
        get() = listOf(
            StringWrapperToStringConverter(),
            StringToStringWrapperConverter(),
        )

    data class StringWrapper(val value: String)

    @WritingConverter
    class StringWrapperToStringConverter : Converter<StringWrapper, String> {
        override fun convert(source: StringWrapper): String = source.value
    }

    @ReadingConverter
    class StringToStringWrapperConverter : Converter<String, StringWrapper> {
        override fun convert(source: String): StringWrapper = StringWrapper(source)
    }

    @Test
    fun `list in an IN clause is expanded with each element converted`() = runTest {
        // given
        kueryClient.sql {
            +"""
            INSERT INTO converter (text) VALUES
            ('text1'),
            ('text2'),
            ('text3');
            """.trimIndent()
        }.rowsUpdated()

        // when & then
        val result = kueryClient.sql {
            val inList = listOf(StringWrapper("text1"), StringWrapper("text2"))
            +"SELECT * FROM converter WHERE text IN ($inList)"
        }.listMap()
        assertThat(result).isEqualTo(listOf(mapOf("id" to 1L, "text" to "text1"), mapOf("id" to 2L, "text" to "text2")))
    }

    @Test
    fun `list of arrays expands into composite IN tuples`() = runTest {
        // given
        kueryClient.sql {
            +"""
            INSERT INTO converter (text) VALUES
            ('text1'),
            ('text2');
            """.trimIndent()
        }.rowsUpdated()

        // when & then
        val result = kueryClient.sql {
            val pairs = listOf(arrayOf<Any>(1L, StringWrapper("text1")))
            +"SELECT * FROM converter WHERE (id, text) IN ($pairs)"
        }.listMap()
        assertThat(result).isEqualTo(listOf(mapOf("id" to 1L, "text" to "text1")))
    }

    @Test
    fun `empty collection in an IN clause returns no rows on H2`() = runTest {
        // An empty collection is bound as-is; Spring's named parameter expansion then renders
        // it as `IN ()`. Whether that is valid SQL depends on the database: H2 accepts it and
        // returns no rows, while MySQL/PostgreSQL reject it (see the mysql/postgres packages).
        // Callers targeting those databases must guard against empty collections themselves.

        // given
        kueryClient.sql {
            +"INSERT INTO converter (text) VALUES ('text1')"
        }.rowsUpdated()

        // when & then
        val result = kueryClient.sql {
            val emptyIds = emptyList<Long>()
            +"SELECT * FROM converter WHERE id IN ($emptyIds)"
        }.listMap()
        assertThat(result).isEqualTo(emptyList())
    }

    @OptIn(DelicateKueryClientApi::class)
    @Test
    fun `tuple IN clause hand-built with addUnsafe and bind matches the given pairs`() = runTest {
        // given
        kueryClient.sql {
            +"""
            INSERT INTO converter (text) VALUES
            ('text1'),
            ('text2'),
            ('text3');
            """.trimIndent()
        }.rowsUpdated()

        // when & then
        val pairs = listOf(1L to "text1", 3L to "text3")
        val result = kueryClient.sql {
            +"SELECT * FROM converter"
            addUnsafe("WHERE (id, text) IN (${pairs.joinToString(", ") { "(${bind(it.first)}, ${bind(it.second)})" }})")
        }.listMap()
        assertThat(result).isEqualTo(listOf(mapOf("id" to 1L, "text" to "text1"), mapOf("id" to 3L, "text" to "text3")))
    }

    @OptIn(DelicateKueryClientApi::class)
    @Test
    fun `collection bound via bind inside addUnsafe is expanded in the IN clause`() = runTest {
        // given
        kueryClient.sql {
            +"""
            INSERT INTO converter (text) VALUES
            ('text1'),
            ('text2'),
            ('text3');
            """.trimIndent()
        }.rowsUpdated()

        // when & then
        val result = kueryClient.sql {
            val texts = listOf("text1", "text2")
            addUnsafe("SELECT * FROM converter WHERE text IN (${bind(texts)})")
        }.listMap()
        assertThat(result).isEqualTo(listOf(mapOf("id" to 1L, "text" to "text1"), mapOf("id" to 2L, "text" to "text2")))
    }

    @Test
    fun `set in an IN clause is expanded like a list`() = runTest {
        // given
        insertThreeRows()

        // when & then
        val result = kueryClient.sql {
            val inSet = setOf(StringWrapper("text1"), StringWrapper("text2"))
            +"SELECT * FROM converter WHERE text IN ($inSet)"
        }.listMap()
        assertThat(result).isEqualTo(listOf(mapOf("id" to 1L, "text" to "text1"), mapOf("id" to 2L, "text" to "text2")))
    }

    @Test
    fun `map keys view in an IN clause is expanded like a collection`() = runTest {
        // given
        insertThreeRows()

        // when & then
        val map = mapOf(StringWrapper("text1") to 1, StringWrapper("text3") to 3)
        val result = kueryClient.sql {
            +"SELECT * FROM converter WHERE text IN (${map.keys})"
        }.listMap()
        assertThat(result).isEqualTo(listOf(mapOf("id" to 1L, "text" to "text1"), mapOf("id" to 3L, "text" to "text3")))
    }

    @Test
    fun `custom Collection subtype in an IN clause is expanded like a list`() = runTest {
        // Non-standard Collection implementations (e.g. NonEmptyList of functional libraries)
        // must expand like any other Collection.
        class NonEmptyList<T>(private val delegate: List<T>) : Collection<T> by delegate

        // given
        insertThreeRows()

        // when & then
        val result = kueryClient.sql {
            val inList = NonEmptyList(listOf(StringWrapper("text1"), StringWrapper("text2")))
            +"SELECT * FROM converter WHERE text IN ($inList)"
        }.listMap()
        assertThat(result).isEqualTo(listOf(mapOf("id" to 1L, "text" to "text1"), mapOf("id" to 2L, "text" to "text2")))
    }

    protected suspend fun insertThreeRows() {
        kueryClient.sql {
            +"""
            INSERT INTO converter (text) VALUES
            ('text1'),
            ('text2'),
            ('text3');
            """.trimIndent()
        }.rowsUpdated()
    }
}
