package dev.hsbrysk.kuery.spring.jdbc.conversion

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import dev.hsbrysk.kuery.core.single
import dev.hsbrysk.kuery.spring.jdbc.H2TestDatabase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.WritingConverter

/**
 * Binding-side (write) support for Kotlin value classes: a value class is transparently
 * unwrapped to its underlying value, like enums are bound by name. Fetch-side mapping is
 * covered separately in `mapping.ValueClassFetchTest`.
 */
class ValueClassConversionTest {
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

    private val kueryClient = h2.kueryClient()

    @BeforeEach
    fun beforeEach() {
        h2.setUpForConverterTest()
    }

    @AfterEach
    fun afterEach() {
        h2.tearDownForConverterTest()
    }

    @Test
    fun `value class is bound as its underlying value`() {
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
    fun `value class wrapping null is bound as SQL NULL`() {
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
    fun `value class wrapping an enum is bound as the enum name`() {
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
    fun `nested value class is unwrapped recursively`() {
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
    fun `value class wrapping a null enum is bound as SQL NULL`() {
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
    fun `value class wrapping a nullable value class is unwrapped recursively`() {
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
    fun `value class wrapping a null value class is bound as SQL NULL`() {
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
    fun `file-private value class is bound as its underlying value`() {
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
    fun `value class in an IN clause matches the row stored by its underlying value`() {
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
    fun `value class in a composite IN tuple matches the row stored by its underlying value`() {
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
    fun `registered writing converter takes precedence over automatic unwrapping`() {
        // given
        val kueryClient = h2.kueryClient(listOf(UserNameToStringConverter()))

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

    @WritingConverter
    class UserNameToStringConverter : Converter<UserName, String> {
        override fun convert(source: UserName): String = "custom:${source.value}"
    }

    companion object {
        private val h2 = H2TestDatabase()
    }
}

// A top-level private value class compiles to a package-private JVM class, exercising the
// accessibility handling of the unbox reflection.
@JvmInline
private value class PrivateName(val value: String)
