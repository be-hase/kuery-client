package dev.hsbrysk.kuery.spring.r2dbc.mysql

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import dev.hsbrysk.kuery.core.DelicateKueryClientApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.core.convert.converter.Converter
import org.springframework.dao.DataRetrievalFailureException
import org.springframework.data.convert.ReadingConverter
import java.net.URI
import kotlin.reflect.KClass

class MySqlSingleBasicTypeTest {
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

    @OptIn(DelicateKueryClientApi::class)
    @ParameterizedTest
    @MethodSource("singleValues")
    fun `single maps a single-column result to each basic type`(
        query: String,
        expected: Any,
        type: KClass<*>,
    ) = runTest {
        // when
        val result = kueryClient.sql {
            addUnsafe(query)
        }.single(type)

        // then
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `single with a non-simple data class type is rejected`() = runTest {
        assertFailure {
            kueryClient.sql {
                +"SELECT 'hoge'"
            }.single(StringWrapper::class)
        }.isInstanceOf(DataRetrievalFailureException::class)
    }

    @Test
    fun `list maps each row of a single-column result`() = runTest {
        // when
        val result = kueryClient.sql {
            +"SELECT 1 UNION SELECT 0"
        }.list(Int::class)

        // then
        assertThat(result).isEqualTo(listOf(1, 0))
    }

    companion object {
        private val mysql = MySqlTestContainer

        @JvmStatic
        fun singleValues(): List<Any> = listOf(
            Arguments.of("SELECT 1", 1.toShort(), Short::class),
            Arguments.of("SELECT 1", 1, Int::class),
            Arguments.of("SELECT 1", 1L, Long::class),
            Arguments.of("SELECT '1'", 1.toShort(), Short::class),
            Arguments.of("SELECT '1'", 1, Int::class),
            Arguments.of("SELECT '1'", 1L, Long::class),
            Arguments.of("SELECT 'hoge'", "hoge", String::class),
            Arguments.of("SELECT 1", "1", String::class),
            Arguments.of("SELECT 'https://example.com'", URI("https://example.com"), URI::class),
            // Unlike JDBC, this test case does not pass.
            // Arguments.of("SELECT 1", true, Boolean::class),
            // Arguments.of("SELECT 0", false, Boolean::class),
        )
    }
}
