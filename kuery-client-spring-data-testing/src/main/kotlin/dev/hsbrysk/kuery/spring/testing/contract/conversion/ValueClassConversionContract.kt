package dev.hsbrysk.kuery.spring.testing.contract.conversion

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import dev.hsbrysk.kuery.core.single
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.WritingConverter

/**
 * Binding-side (write) support for Kotlin value classes: a value class is transparently
 * unwrapped to its underlying value, like enums are bound by name. Fetch-side mapping is
 * covered separately in `mapping.ValueClassFetchTest`.
 *
 * Contract shared by the jdbc and r2dbc modules. Each module runs it via a concrete subclass
 * that supplies its `ContractDatabase`.
 */
abstract class ValueClassConversionContract : ConverterContractBase() {
    @JvmInline
    value class UserName(val value: String)

    @JvmInline
    value class OptionalText(val value: String?)

    enum class SampleEnum {
        HOGE,
    }

    @JvmInline
    value class Status(val value: SampleEnum)

    @JvmInline
    value class Outer(val inner: UserName)

    @JvmInline
    value class OptionalStatus(val value: SampleEnum?)

    @JvmInline
    value class OptionalName(val value: UserName?)

    @JvmInline
    value class Age(val value: Int)

    @JvmInline
    value class OptionalAge(val value: Int?)

    data class CustomType(val raw: String)

    @JvmInline
    value class WrappedCustomType(val value: CustomType)

    @Test
    fun `value class is bound as its underlying value`() = runTest {
        // given
        kueryClient.sql {
            +"INSERT INTO converter (text) VALUES (${UserName("hoge")})"
        }.rowsUpdated()

        // when & then
        val map = kueryClient.sql {
            +"SELECT * FROM converter"
        }.singleMap()
        assertThat(map["text"]).isEqualTo("hoge")
    }

    @Test
    fun `value class wrapping null is bound as SQL NULL`() = runTest {
        // given
        kueryClient.sql {
            +"INSERT INTO converter (text) VALUES (${OptionalText(null)})"
        }.rowsUpdated()

        // when & then
        val map = kueryClient.sql {
            +"SELECT * FROM converter"
        }.singleMap()
        assertThat(map["text"]).isNull()
    }

    @Test
    fun `value class wrapping an enum is bound as the enum name`() = runTest {
        // given
        kueryClient.sql {
            +"INSERT INTO converter (text) VALUES (${Status(SampleEnum.HOGE)})"
        }.rowsUpdated()

        // when & then
        val map = kueryClient.sql {
            +"SELECT * FROM converter"
        }.singleMap()
        assertThat(map["text"]).isEqualTo("HOGE")
    }

    @Test
    fun `nested value class is unwrapped recursively`() = runTest {
        // given
        kueryClient.sql {
            +"INSERT INTO converter (text) VALUES (${Outer(UserName("hoge"))})"
        }.rowsUpdated()

        // when & then
        val map = kueryClient.sql {
            +"SELECT * FROM converter"
        }.singleMap()
        assertThat(map["text"]).isEqualTo("hoge")
    }

    @Test
    fun `value class wrapping a null enum is bound as SQL NULL`() = runTest {
        // given
        kueryClient.sql {
            +"INSERT INTO converter (text) VALUES (${OptionalStatus(null)})"
        }.rowsUpdated()

        // when & then
        val map = kueryClient.sql {
            +"SELECT * FROM converter"
        }.singleMap()
        assertThat(map["text"]).isNull()
    }

    @Test
    fun `value class wrapping a nullable value class is unwrapped recursively`() = runTest {
        // given
        kueryClient.sql {
            +"INSERT INTO converter (text) VALUES (${OptionalName(UserName("hoge"))})"
        }.rowsUpdated()

        // when & then
        val map = kueryClient.sql {
            +"SELECT * FROM converter"
        }.singleMap()
        assertThat(map["text"]).isEqualTo("hoge")
    }

    @Test
    fun `value class wrapping a null value class is bound as SQL NULL`() = runTest {
        // given
        kueryClient.sql {
            +"INSERT INTO converter (text) VALUES (${OptionalName(null)})"
        }.rowsUpdated()

        // when & then
        val map = kueryClient.sql {
            +"SELECT * FROM converter"
        }.singleMap()
        assertThat(map["text"]).isNull()
    }

    @Test
    fun `value class wrapping a primitive is bound as its underlying value`() = runTest {
        // when & then
        val value: Int = kueryClient.sql {
            +"SELECT ${Age(42)}"
        }.single()
        assertThat(value).isEqualTo(42)
    }

    @Test
    fun `value class wrapping a null primitive is bound as SQL NULL`() = runTest {
        // given
        kueryClient.sql {
            +"INSERT INTO converter (text) VALUES (${OptionalAge(null)})"
        }.rowsUpdated()

        // when & then
        val map = kueryClient.sql {
            +"SELECT * FROM converter"
        }.singleMap()
        assertThat(map["text"]).isNull()
    }

    @Test
    fun `file-private value class is bound as its underlying value`() = runTest {
        // given
        kueryClient.sql {
            +"INSERT INTO converter (text) VALUES (${PrivateName("hoge")})"
        }.rowsUpdated()

        // when & then
        val map = kueryClient.sql {
            +"SELECT * FROM converter"
        }.singleMap()
        assertThat(map["text"]).isEqualTo("hoge")
    }

    @Test
    fun `value class in an IN clause matches the row stored by its underlying value`() = runTest {
        // given
        kueryClient.sql {
            +"INSERT INTO converter (text) VALUES (${UserName("hoge")})"
        }.rowsUpdated()

        // when & then
        val count: Long = kueryClient.sql {
            +"SELECT COUNT(*) FROM converter WHERE text IN (${listOf(UserName("hoge"))})"
        }.single()
        assertThat(count).isEqualTo(1L)
    }

    @Test
    fun `value class in a composite IN tuple matches the row stored by its underlying value`() = runTest {
        // given
        kueryClient.sql {
            +"INSERT INTO converter (text) VALUES (${UserName("hoge")})"
        }.rowsUpdated()

        // when & then
        val count: Long = kueryClient.sql {
            val pairs = listOf(arrayOf<Any>(1L, UserName("hoge")))
            +"SELECT COUNT(*) FROM converter WHERE (id, text) IN ($pairs)"
        }.single()
        assertThat(count).isEqualTo(1L)
    }

    @Test
    fun `registered writing converter takes precedence over automatic unwrapping`() = runTest {
        // given
        val kueryClient = database.kueryClient(listOf(UserNameToStringConverter()))

        // when
        kueryClient.sql {
            +"INSERT INTO converter (text) VALUES (${UserName("hoge")})"
        }.rowsUpdated()

        // then
        val map = kueryClient.sql {
            +"SELECT * FROM converter"
        }.singleMap()
        assertThat(map["text"]).isEqualTo("custom:hoge")
    }

    @Test
    fun `unwrapped underlying value goes through its own writing converter`() = runTest {
        // No converter targets WrappedCustomType itself, so it is unwrapped to its CustomType
        // underlying, which is then re-dispatched through the conversion pipeline and picked up by
        // the CustomType -> String writing converter.
        // given
        val kueryClient = database.kueryClient(listOf(CustomTypeToStringConverter()))

        // when
        kueryClient.sql {
            +"INSERT INTO converter (text) VALUES (${WrappedCustomType(CustomType("hoge"))})"
        }.rowsUpdated()

        // then
        val map = kueryClient.sql {
            +"SELECT * FROM converter"
        }.singleMap()
        assertThat(map["text"]).isEqualTo("custom:hoge")
    }

    @WritingConverter
    class UserNameToStringConverter : Converter<UserName, String> {
        override fun convert(source: UserName): String = "custom:${source.value}"
    }

    @WritingConverter
    class CustomTypeToStringConverter : Converter<CustomType, String> {
        override fun convert(source: CustomType): String = "custom:${source.raw}"
    }
}

// A top-level private value class compiles to a package-private JVM class, exercising the
// accessibility handling of the unbox reflection.
@JvmInline
private value class PrivateName(val value: String)
