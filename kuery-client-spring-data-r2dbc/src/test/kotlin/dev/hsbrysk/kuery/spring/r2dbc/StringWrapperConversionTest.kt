package dev.hsbrysk.kuery.spring.r2dbc

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.single
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.core.convert.converter.Converter

class StringWrapperConversionTest : MysqlTestContainersBase() {
    override fun converters(): List<Any> {
        return listOf(
            StringWrapperToStringConverter(),
            StringToStringWrapperConverter(),
        )
    }

    data class StringWrapper(val value: String)

    data class Record(
        val text: StringWrapper,
    )

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
    fun test() = runTest {
        kueryClient.sql {
            +"INSERT INTO converter (text) VALUES (${bind(StringWrapper("hoge"))})"
        }.rowsUpdated()

        val record: Record = kueryClient.sql {
            +"SELECT * FROM converter"
        }.single()

        assertThat(record.text).isEqualTo(StringWrapper("hoge"))
    }
}
