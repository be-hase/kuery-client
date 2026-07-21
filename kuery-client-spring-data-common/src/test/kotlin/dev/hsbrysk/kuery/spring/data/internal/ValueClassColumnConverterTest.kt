@file:OptIn(KueryClientInternalApi::class)

package dev.hsbrysk.kuery.spring.data.internal

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import dev.hsbrysk.kuery.core.KueryClientInternalApi
import org.junit.jupiter.api.Test
import org.springframework.core.convert.converter.Converter
import org.springframework.core.convert.support.DefaultConversionService
import java.math.BigDecimal

/**
 * Direct unit tests for [ValueClassColumnConverter], driving the driver typed retrieval through a
 * controlled lambda instead of a real database. This pins the reading-converter/boxing priority for
 * both source types a converter can be keyed on — the raw column type and the type the driver
 * produces for the underlying retrieval — including the driver-typed-converter case that r2dbc-h2
 * cannot reproduce through an integration test (its `readable.get` does not coerce a numeric column
 * to another type).
 */
class ValueClassColumnConverterTest {
    @JvmInline
    value class StringId(val value: String)

    @JvmInline
    value class UserName(val value: String)

    @JvmInline
    value class Wrapper(val value: UserName)

    @JvmInline
    value class Amount(val value: Int)

    @JvmInline
    value class Money(val value: BigDecimal)

    @JvmInline
    value class MoneyBox(val value: Money)

    class LongToStringIdConverter : Converter<Long, StringId> {
        override fun convert(source: Long): StringId = StringId("raw:$source")
    }

    class StringToStringIdConverter : Converter<String, StringId> {
        override fun convert(source: String): StringId = StringId("typed:$source")
    }

    class BlankToNullBigDecimalConverter : Converter<String, BigDecimal?> {
        override fun convert(source: String): BigDecimal? = source.ifBlank { null }?.let { BigDecimal(it) }
    }

    private fun conversionService(vararg converters: Converter<*, *>) = DefaultConversionService().apply {
        converters.forEach { addConverter(it) }
    }

    // Asserts the driver typed retrieval is not consulted when an earlier rule already resolves the value.
    private val noDriverRetrieval: (Class<*>) -> Any? = { error("driver typed retrieval must not run for this case") }

    @Test
    fun `a reading converter keyed on the raw column type wins over boxing`() {
        // given
        val converter = ValueClassColumnConverter(StringId::class, conversionService(LongToStringIdConverter()))

        // when & then
        val result = converter.convert(1L, noDriverRetrieval)
        assertThat(result).isEqualTo(StringId("raw:1"))
    }

    @Test
    fun `a raw value already of the underlying type is boxed without a driver retrieval`() {
        // given
        val converter = ValueClassColumnConverter(UserName::class, conversionService())

        // when & then
        val result = converter.convert("x", noDriverRetrieval)
        assertThat(result).isEqualTo(UserName("x"))
    }

    @Test
    fun `a nested value class is boxed recursively`() {
        // given
        val converter = ValueClassColumnConverter(Wrapper::class, conversionService())

        // when & then
        val result = converter.convert("x") { "x" }
        assertThat(result).isEqualTo(Wrapper(UserName("x")))
    }

    @Test
    fun `a reading converter keyed on the driver typed value wins over boxing`() {
        // The raw Long has no Converter<Long, StringId>; the driver produces the String underlying,
        // and a Converter<String, StringId> keyed on that produced type must win over boxing (which
        // would yield StringId("1")). This is the case r2dbc-h2 cannot reproduce via a real query.
        // given
        val converter = ValueClassColumnConverter(StringId::class, conversionService(StringToStringIdConverter()))

        // when & then
        val result = converter.convert(1L) { type -> if (type == String::class.java) "1" else null }
        assertThat(result).isEqualTo(StringId("typed:1"))
    }

    @Test
    fun `the driver typed value is coerced to the underlying type when no converter matches`() {
        // The driver returns a Long for the Int underlying retrieval; with no converter, it is
        // coerced to the underlying Int through the ConversionService before boxing.
        // given
        val converter = ValueClassColumnConverter(Amount::class, conversionService())

        // when & then
        val result = converter.convert(1L) { 1L }
        assertThat(result).isEqualTo(Amount(1))
    }

    @Test
    fun `a converter returning null maps to null instead of boxing null`() {
        // A converter for the underlying type may legitimately return null (the Converter contract
        // allows it); the value class maps to null rather than being boxed around null.
        // given
        val converter = ValueClassColumnConverter(Money::class, conversionService(BlankToNullBigDecimalConverter()))

        // when & then
        val result = converter.convert("") { "" }
        assertThat(result).isNull()
    }

    @Test
    fun `a nested value class maps to null when the inner conversion yields null`() {
        // The recursive converter for the nested level may resolve to null (here through a
        // converter legitimately returning null); the outer level must map to null as well
        // instead of boxing null.
        // given
        val converter = ValueClassColumnConverter(MoneyBox::class, conversionService(BlankToNullBigDecimalConverter()))

        // when & then
        val result = converter.convert("") { null }
        assertThat(result).isNull()
    }

    @Test
    fun `hasValueClassConstructorParameters is false for a class without a primary constructor`() {
        // A Java class has no Kotlin primary constructor; it must stay on Spring's own mappers
        // instead of being routed to the value class mapper.
        assertThat(hasValueClassConstructorParameters(StringBuilder::class)).isFalse()
    }
}
