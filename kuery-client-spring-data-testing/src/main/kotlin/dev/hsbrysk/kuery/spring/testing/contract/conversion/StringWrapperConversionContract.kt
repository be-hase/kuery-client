package dev.hsbrysk.kuery.spring.testing.contract.conversion

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.single
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter

/**
 * Contract shared by the jdbc and r2dbc modules. Each module runs it via a concrete subclass
 * that supplies its `ContractDatabase`.
 */
abstract class StringWrapperConversionContract : ConverterContractBase() {
    override fun createConverters(): List<Any> = listOf(
        StringWrapperToStringConverter(),
        StringToStringWrapperConverter(),
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

    @Test
    fun `wrapper type round-trips through registered writing and reading converters`() = runTest {
        // given
        kueryClient.sql {
            +"INSERT INTO converter (text) VALUES (${StringWrapper("hoge")})"
        }.rowsUpdated()

        // when
        val record: Record = kueryClient.sql {
            +"SELECT * FROM converter"
        }.single()

        // then
        assertThat(record.text).isEqualTo(StringWrapper("hoge"))
    }
}
