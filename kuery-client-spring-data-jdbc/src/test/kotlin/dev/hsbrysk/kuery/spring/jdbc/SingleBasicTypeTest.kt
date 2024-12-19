package dev.hsbrysk.kuery.spring.jdbc

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.net.URI
import kotlin.reflect.KClass

class SingleBasicTypeTest {
    private val kueryClient = mysql.kueryClient()

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
            Arguments.of("SELECT 1", 1, Int::class),
            Arguments.of("""SELECT "https://example.com"""", URI("https://example.com"), URI::class),
            Arguments.of("""SELECT 1""", true, Boolean::class),
            Arguments.of("""SELECT 0""", false, Boolean::class),
        )
    }
}
