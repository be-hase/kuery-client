package dev.hsbrysk.kuery.spring.jdbc

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import org.springframework.core.convert.converter.Converter

class CollectionConversionTest : MySQLTestContainersBase() {
    override fun converters(): List<Any> {
        return listOf(
            StringWrapperToStringConverter(),
            StringToStringWrapperConverter(),
        )
    }

    data class StringWrapper(val value: String)

    class StringWrapperToStringConverter : Converter<StringWrapper, String> {
        override fun convert(source: StringWrapper): String {
            return source.value
        }
    }

    class StringToStringWrapperConverter : Converter<String, StringWrapper> {
        override fun convert(source: String): StringWrapper {
            return StringWrapper(source)
        }
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
            +"SELECT * FROM converter WHERE text IN (${bind(inList)})"
        }.listMap()
        assertThat(result).isEqualTo(listOf(mapOf("id" to 1L, "text" to "text1"), mapOf("id" to 2L, "text" to "text2")))
    }
}
