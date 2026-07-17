package dev.hsbrysk.kuery.spring.jdbc.conversion

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import dev.hsbrysk.kuery.spring.jdbc.H2TestDatabase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.WritingConverter

/**
 * Spring's [Converter] contract allows returning null; a null result must be bound as SQL NULL
 * (the same way Spring Data itself writes null-converted properties).
 */
class NullReturningWritingConverterTest {
    private val kueryClient = h2.kueryClient(listOf(NullableTextToStringConverter()))

    data class NullableText(val value: String?)

    @WritingConverter
    class NullableTextToStringConverter : Converter<NullableText, String?> {
        override fun convert(source: NullableText): String? = source.value
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
    fun `null converter result is bound as SQL NULL`() {
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
    fun `non-null converter result is bound as the converted value`() {
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

    companion object {
        private val h2 = H2TestDatabase()
    }
}
