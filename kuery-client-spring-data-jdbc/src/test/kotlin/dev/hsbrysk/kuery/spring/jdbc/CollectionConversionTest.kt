package dev.hsbrysk.kuery.spring.jdbc

import assertk.assertThat
import assertk.assertions.isEqualTo
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
    fun beforeEach() {
        h2.setUpForConverterTest()
    }

    @AfterEach
    fun afterEach() {
        h2.tearDownForConverterTest()
    }

    @Test
    fun test() {
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
    fun testCompositeIn() {
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

    companion object {
        private val h2 = H2TestDatabase()
    }
}
