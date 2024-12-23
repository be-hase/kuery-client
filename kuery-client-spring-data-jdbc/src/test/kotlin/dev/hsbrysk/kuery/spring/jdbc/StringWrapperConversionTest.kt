package dev.hsbrysk.kuery.spring.jdbc

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.single
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter

class StringWrapperConversionTest {
    private val kueryClient = mysql.kueryClient(
        listOf(
            StringWrapperToStringConverter(),
            StringToStringWrapperConverter(),
        ),
    )

    data class StringWrapper(val value: String)

    data class Record(val text: StringWrapper)

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
        mysql.setUpForConverterTest()
    }

    @AfterEach
    fun afterEach() {
        mysql.tearDownForConverterTest()
    }

    @Test
    fun test() {
        kueryClient.sql {
            +"INSERT INTO converter (text) VALUES (${StringWrapper("hoge")})"
        }.rowsUpdated()

        val record: Record = kueryClient.sql {
            +"SELECT * FROM converter"
        }.single()

        assertThat(record.text).isEqualTo(StringWrapper("hoge"))
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
