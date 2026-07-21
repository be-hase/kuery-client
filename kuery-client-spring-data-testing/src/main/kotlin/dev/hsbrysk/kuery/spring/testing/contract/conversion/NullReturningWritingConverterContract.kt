package dev.hsbrysk.kuery.spring.testing.contract.conversion

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.WritingConverter

/**
 * Spring's [Converter] contract allows returning null; a null result must be bound as SQL NULL
 * (the same way Spring Data itself writes null-converted properties).
 *
 * Contract shared by the jdbc and r2dbc modules. Each module runs it via a concrete subclass
 * that supplies its `ContractDatabase`.
 */
abstract class NullReturningWritingConverterContract : ConverterContractBase() {
    override fun createConverters(): List<Any> = listOf(NullableTextToStringConverter())

    data class NullableText(val value: String?)

    @WritingConverter
    class NullableTextToStringConverter : Converter<NullableText, String?> {
        override fun convert(source: NullableText): String? = source.value
    }

    @Test
    fun `null converter result is bound as SQL NULL`() = runTest {
        // given
        kueryClient.sql {
            +"INSERT INTO converter (text) VALUES (${NullableText(null)})"
        }.rowsUpdated()

        // when & then
        val map = kueryClient.sql {
            +"SELECT * FROM converter"
        }.singleMap()
        assertThat(map["text"]).isNull()
    }

    @Test
    fun `non-null converter result is bound as the converted value`() = runTest {
        // given
        kueryClient.sql {
            +"INSERT INTO converter (text) VALUES (${NullableText("hoge")})"
        }.rowsUpdated()

        // when & then
        val map = kueryClient.sql {
            +"SELECT * FROM converter"
        }.singleMap()
        assertThat(map["text"]).isEqualTo("hoge")
    }
}
