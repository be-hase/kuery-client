package dev.hsbrysk.kuery.spring.r2dbc

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.convert.converter.Converter

class CollectionConversionTest {
    private val kueryClient = mysql.kueryClient(
        listOf(
            StringWrapperToStringConverter(),
            StringToStringWrapperConverter(),
        ),
    )

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

    @BeforeEach
    fun beforeEach() = runTest {
        mysql.setUpForConverterTest()
    }

    @AfterEach
    fun afterEach() = runTest {
        mysql.tearDownForConverterTest()
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
            +"SELECT * FROM converter WHERE text IN (${bind(inList)})"
        }.listMap()
        assertThat(result).isEqualTo(listOf(mapOf("id" to 1L, "text" to "text1"), mapOf("id" to 2L, "text" to "text2")))
    }

    companion object {
        private val mysql = MySqlTestContainer()

        @AfterAll
        @JvmStatic
        fun afterAll() {
            mysql.close()
        }
    }
}
