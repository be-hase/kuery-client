package dev.hsbrysk.kuery.spring.jdbc.conversion

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.single
import dev.hsbrysk.kuery.spring.jdbc.H2TestDatabase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.convert.TypeDescriptor
import org.springframework.core.convert.converter.Converter
import org.springframework.core.convert.converter.GenericConverter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter

/**
 * Pins which converter registration styles are supported beyond plain annotated Converter pairs
 * (see StringWrapperConversionTest) and ConverterFactory (see CodeEnumConversionTest).
 *
 * Note that reading converters for non-simple wrapper types apply on the DataClassRowMapper
 * property path; reading a wrapper as a single scalar column is unsupported regardless of
 * converter registration (see BasicUsageTest's unSupportNotSimpleProperty).
 */
class ConverterRegistrationTest {
    data class StringWrapper(val value: String)

    data class IntWrapper(val value: Int)

    data class Record(val text: StringWrapper)

    data class IntRecord(val num: IntWrapper)

    @ReadingConverter
    class StringToStringWrapperGenericConverter : GenericConverter {
        override fun getConvertibleTypes(): Set<GenericConverter.ConvertiblePair> =
            setOf(GenericConverter.ConvertiblePair(String::class.java, StringWrapper::class.java))

        override fun convert(
            source: Any?,
            sourceType: TypeDescriptor,
            targetType: TypeDescriptor,
        ): Any = StringWrapper(source as String)
    }

    @ReadingConverter
    class IntToIntWrapperGenericConverter : GenericConverter {
        // Int::class.java (primitive int) would never match: JDBC values arrive boxed, so the
        // pair must be registered with the boxed java.lang.Integer type (javaObjectType).
        override fun getConvertibleTypes(): Set<GenericConverter.ConvertiblePair> =
            setOf(GenericConverter.ConvertiblePair(Int::class.javaObjectType, IntWrapper::class.java))

        override fun convert(
            source: Any?,
            sourceType: TypeDescriptor,
            targetType: TypeDescriptor,
        ): Any = IntWrapper(source as Int)
    }

    @WritingConverter
    class StringWrapperToStringConverter : Converter<StringWrapper, String> {
        override fun convert(source: StringWrapper): String = source.value
    }

    class UnannotatedStringWrapperToStringConverter : Converter<StringWrapper, String> {
        override fun convert(source: StringWrapper): String = source.value
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
    fun `GenericConverter is supported for reading`() {
        // given
        val kueryClient = h2.kueryClient(
            listOf(StringToStringWrapperGenericConverter(), StringWrapperToStringConverter()),
        )
        kueryClient.sql {
            +"INSERT INTO converter (text) VALUES ('hoge')"
        }.rowsUpdated()

        // when & then
        val record: Record = kueryClient.sql {
            +"SELECT * FROM converter"
        }.single()
        assertThat(record.text).isEqualTo(StringWrapper("hoge"))
    }

    @Test
    fun `GenericConverter for a Kotlin Int works via javaObjectType`() {
        // given
        val kueryClient = h2.kueryClient(listOf(IntToIntWrapperGenericConverter()))

        // when & then
        val record: IntRecord = kueryClient.sql {
            +"SELECT CAST(42 AS INT) AS num"
        }.single()
        assertThat(record).isEqualTo(IntRecord(IntWrapper(42)))
    }

    @Test
    fun `converter without annotation infers direction from the store-typed side`() {
        // Converter<StringWrapper, String> targets a store type, so Spring infers it as a
        // writing converter even without @WritingConverter.

        // given
        val kueryClient = h2.kueryClient(listOf(UnannotatedStringWrapperToStringConverter()))

        kueryClient.sql {
            +"INSERT INTO converter (text) VALUES (${StringWrapper("hoge")})"
        }.rowsUpdated()

        // when & then
        val result = kueryClient.sql {
            +"SELECT text FROM converter"
        }.single<String>()
        assertThat(result).isEqualTo("hoge")
    }

    companion object {
        private val h2 = H2TestDatabase()
    }
}
