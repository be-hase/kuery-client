package dev.hsbrysk.kuery.spring.jdbc

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.jdbc.BadSqlGrammarException
import java.net.URI
import kotlin.reflect.KClass

class SingleBasicTypeTest {
    private val kueryClient = mysql.kueryClient(
        listOf(
            StringToStringWrapperConverter(),
        ),
    )

    data class StringWrapper(val value: String)

    @ReadingConverter
    class StringToStringWrapperConverter : Converter<String, StringWrapper> {
        override fun convert(source: String): StringWrapper = StringWrapper(source)
    }

    @ParameterizedTest
    @MethodSource("singleValues")
    fun testSingleValues(
        query: String,
        expected: Any,
        type: KClass<*>,
    ) {
        val result = kueryClient.sql {
            +query
        }.single(type)

        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun unSupportNotSimpleProperty() {
        assertFailure {
            kueryClient.sql {
                +"SELECT 'hoge'"
            }.single(StringWrapper::class)
        }.isInstanceOf(BadSqlGrammarException::class)
    }

    @Test
    fun testSingleColumnWithMultiValue() {
        val result = kueryClient.sql {
            +"SELECT 1 UNION SELECT 0"
        }.list(Int::class)

        assertThat(result).isEqualTo(listOf(1, 0))
    }

    companion object {
        private val mysql = MySqlTestContainer()

        @AfterAll
        @JvmStatic
        fun afterAll() {
            mysql.close()
        }

        @JvmStatic
        fun singleValues(): List<Any> = listOf(
            Arguments.of("SELECT 1", 1.toShort(), Short::class),
            Arguments.of("SELECT 1", 1, Int::class),
            Arguments.of("SELECT 1", 1L, Long::class),
            Arguments.of("SELECT '1'", 1.toShort(), Short::class),
            Arguments.of("SELECT '1'", 1, Int::class),
            Arguments.of("SELECT '1'", 1L, Long::class),
            Arguments.of("SELECT 'hoge'", "hoge", String::class),
            Arguments.of("SELECT 'https://example.com'", URI("https://example.com"), URI::class),
            Arguments.of("SELECT 1", true, Boolean::class),
            Arguments.of("SELECT 0", false, Boolean::class),
        )
    }
}
