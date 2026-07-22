@file:OptIn(KueryClientInternalApi::class)

package dev.hsbrysk.kuery.spring.data.internal

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import dev.hsbrysk.kuery.core.KueryClientInternalApi
import org.junit.jupiter.api.Test
import org.springframework.core.convert.converter.Converter
import org.springframework.core.convert.support.DefaultConversionService
import org.springframework.data.convert.CustomConversions
import org.springframework.data.convert.WritingConverter

/**
 * Direct unit tests for [SpringDataWriteConverter]. Through the clients this class is only
 * reachable via native array binding, which no dialect but PostgreSQL supports, so its component
 * type resolution and defensive branches are pinned here database-independently instead.
 */
class SpringDataWriteConverterTest {
    enum class SampleEnum {
        HOGE,
        FUGA,
    }

    data class StringWrapper(val value: String)

    @JvmInline
    value class UserName(val value: String)

    @JvmInline
    value class Wrapper(val value: UserName)

    @JvmInline
    value class Wrapped<T>(val value: T)

    @WritingConverter
    class StringWrapperToStringConverter : Converter<StringWrapper, String> {
        override fun convert(source: StringWrapper): String = source.value
    }

    @WritingConverter
    class UserNameToIntConverter : Converter<UserName, Int> {
        override fun convert(source: UserName): Int = source.value.length
    }

    private val defaultConverter = converter()

    @Test
    fun `convertArray returns the same instance when no element needs conversion`() {
        // given
        val array = arrayOf("a", "b")

        // when & then
        assertThat(defaultConverter.convertArray(array)).isSameInstanceAs(array)
    }

    @Test
    fun `convertArray returns an empty generic value class array as is`() {
        // No element carries a concrete underlying type to infer a component type from.
        // given
        val array = emptyArray<Wrapped<String>>()

        // when & then
        assertThat(defaultConverter.convertArray(array)).isSameInstanceAs(array)
    }

    @Test
    fun `convertArray returns an all-null generic value class array as is`() {
        // given
        val array = arrayOfNulls<Wrapped<String>>(2)

        // when & then
        assertThat(defaultConverter.convertArray(array)).isSameInstanceAs(array)
    }

    @Test
    fun `convertArray converts an enum array to a String array of names`() {
        // when
        val result = defaultConverter.convertArray(arrayOf(SampleEnum.HOGE, SampleEnum.FUGA))

        // then
        assertThat(result.toList()).isEqualTo(listOf("HOGE", "FUGA"))
        assertThat(result.javaClass.componentType).isEqualTo(String::class.java)
    }

    @Test
    fun `convertArray produces a String array from an empty enum array`() {
        // The component type alone carries the conversion intent when there are no elements.
        // when
        val result = defaultConverter.convertArray(emptyArray<SampleEnum>())

        // then
        assertThat(result.toList()).isEqualTo(emptyList<String>())
        assertThat(result.javaClass.componentType).isEqualTo(String::class.java)
    }

    @Test
    fun `convertArray produces a String array with null elements from an all-null enum array`() {
        // when
        val result = defaultConverter.convertArray(arrayOfNulls<SampleEnum>(2))

        // then
        assertThat(result.toList()).isEqualTo(listOf(null, null))
        assertThat(result.javaClass.componentType).isEqualTo(String::class.java)
    }

    @Test
    fun `convertArray unwraps value class elements and uses the underlying component type`() {
        // when
        val result = defaultConverter.convertArray(arrayOf(UserName("a"), null))

        // then
        assertThat(result.toList()).isEqualTo(listOf("a", null))
        assertThat(result.javaClass.componentType).isEqualTo(String::class.java)
    }

    @Test
    fun `convertArray resolves the component type of a nested value class recursively`() {
        // when
        val result = defaultConverter.convertArray(arrayOf(Wrapper(UserName("a"))))

        // then
        assertThat(result.toList()).isEqualTo(listOf("a"))
        assertThat(result.javaClass.componentType).isEqualTo(String::class.java)
    }

    @Test
    fun `convertArray infers the component type from the elements of a generic value class array`() {
        // when
        val result = defaultConverter.convertArray(arrayOf(Wrapped("a"), null, Wrapped("b")))

        // then
        assertThat(result.toList()).isEqualTo(listOf("a", null, "b"))
        assertThat(result.javaClass.componentType).isEqualTo(String::class.java)
    }

    @Test
    fun `convertArray falls back to an Object array when generic value class elements have mixed underlying types`() {
        // when
        val result = defaultConverter.convertArray(arrayOf(Wrapped("a"), Wrapped(1)))

        // then
        assertThat(result.toList()).isEqualTo(listOf("a", 1))
        assertThat(result.javaClass.componentType).isEqualTo(Any::class.java)
    }

    @Test
    fun `convertArray applies a registered writing converter to the component type`() {
        // given
        val converter = converter(StringWrapperToStringConverter())

        // when
        val result = converter.convertArray(arrayOf(StringWrapper("a")))

        // then
        assertThat(result.toList()).isEqualTo(listOf("a"))
        assertThat(result.javaClass.componentType).isEqualTo(String::class.java)
    }

    @Test
    fun `componentWriteTarget returns null for a generic value class whose underlying erases to Any`() {
        assertThat(defaultConverter.componentWriteTarget(Wrapped::class.java)).isNull()
    }

    @Test
    fun `convertCollection unwraps value classes and converts enums to names`() {
        // when
        val result = defaultConverter.convertCollection(listOf(UserName("a"), SampleEnum.HOGE, null, 1))

        // then
        assertThat(result).isEqualTo(listOf("a", "HOGE", null, 1))
    }

    @Test
    fun `convertCollection converts nested arrays for composite IN rows`() {
        // when
        val result = defaultConverter.convertCollection(listOf(arrayOf<Any>("x", SampleEnum.HOGE)))

        // then
        val row = result.single() as Array<*>
        assertThat(row.toList()).isEqualTo(listOf("x", "HOGE"))
        assertThat(row.javaClass.componentType).isEqualTo(String::class.java)
    }

    @Test
    fun `a writing converter registered for a value class wins over unwrapping`() {
        // given
        val converter = converter(UserNameToIntConverter())

        // when & then
        assertThat(converter.convertCollection(listOf(UserName("ab")))).isEqualTo(listOf(2))
    }

    private fun converter(vararg converters: Converter<*, *>): SpringDataWriteConverter {
        val conversionService = DefaultConversionService().apply { converters.forEach { addConverter(it) } }
        val customConversions = CustomConversions(CustomConversions.StoreConversions.NONE, converters.toList())
        return SpringDataWriteConverter(conversionService, customConversions)
    }
}
