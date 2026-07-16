package dev.hsbrysk.kuery.spring.r2dbc

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.DelicateKueryClientApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter

class CollectionConversionTest {
    private val kueryClient = h2.kueryClient(
        listOf(
            StringWrapperToStringConverter(),
            StringToStringWrapperConverter(),
        ),
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

    @BeforeEach
    fun beforeEach() = runTest {
        h2.setUpForConverterTest()
    }

    @AfterEach
    fun afterEach() = runTest {
        h2.tearDownForConverterTest()
    }

    @Test
    fun test() = runTest {
        kueryClient.sql {
            +"""
            INSERT INTO converter (text) VALUES
            ('text1'),
            ('text2'),
            ('text3');
            """.trimIndent()
        }.rowsUpdated()

        val result = kueryClient.sql {
            val inList = listOf(StringWrapper("text1"), StringWrapper("text2"))
            +"SELECT * FROM converter WHERE text IN ($inList)"
        }.listMap()
        assertThat(result).isEqualTo(listOf(mapOf("id" to 1L, "text" to "text1"), mapOf("id" to 2L, "text" to "text2")))
    }

    @Test
    fun testCompositeIn() = runTest {
        kueryClient.sql {
            +"""
            INSERT INTO converter (text) VALUES
            ('text1'),
            ('text2');
            """.trimIndent()
        }.rowsUpdated()

        val result = kueryClient.sql {
            val pairs = listOf(arrayOf<Any>(1L, StringWrapper("text1")))
            +"SELECT * FROM converter WHERE (id, text) IN ($pairs)"
        }.listMap()
        assertThat(result).isEqualTo(listOf(mapOf("id" to 1L, "text" to "text1")))
    }

    @Test
    fun testEmptyCollection() = runTest {
        // An empty collection is bound as-is; Spring's named parameter expansion then renders
        // it as `IN ()`. Whether that is valid SQL depends on the database: H2 accepts it and
        // returns no rows, while MySQL/PostgreSQL reject it (see the mysql/postgres packages).
        // Callers targeting those databases must guard against empty collections themselves.
        kueryClient.sql {
            +"INSERT INTO converter (text) VALUES ('text1')"
        }.rowsUpdated()

        val result = kueryClient.sql {
            val emptyIds = emptyList<Long>()
            +"SELECT * FROM converter WHERE id IN ($emptyIds)"
        }.listMap()
        assertThat(result).isEqualTo(emptyList())
    }

    @OptIn(DelicateKueryClientApi::class)
    @Test
    fun testHandBuiltTupleIn() = runTest {
        kueryClient.sql {
            +"""
            INSERT INTO converter (text) VALUES
            ('text1'),
            ('text2'),
            ('text3');
            """.trimIndent()
        }.rowsUpdated()

        val pairs = listOf(1L to "text1", 3L to "text3")
        val result = kueryClient.sql {
            +"SELECT * FROM converter"
            addUnsafe("WHERE (id, text) IN (${pairs.joinToString(", ") { "(${bind(it.first)}, ${bind(it.second)})" }})")
        }.listMap()
        assertThat(result).isEqualTo(listOf(mapOf("id" to 1L, "text" to "text1"), mapOf("id" to 3L, "text" to "text3")))
    }

    @OptIn(DelicateKueryClientApi::class)
    @Test
    fun testBindCollectionViaAddUnsafe() = runTest {
        kueryClient.sql {
            +"""
            INSERT INTO converter (text) VALUES
            ('text1'),
            ('text2'),
            ('text3');
            """.trimIndent()
        }.rowsUpdated()

        val result = kueryClient.sql {
            val texts = listOf("text1", "text2")
            addUnsafe("SELECT * FROM converter WHERE text IN (${bind(texts)})")
        }.listMap()
        assertThat(result).isEqualTo(listOf(mapOf("id" to 1L, "text" to "text1"), mapOf("id" to 2L, "text" to "text2")))
    }

    companion object {
        private val h2 = H2TestDatabase()
    }
}
