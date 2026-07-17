package dev.hsbrysk.kuery.spring.jdbc

import assertk.assertFailure
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.message
import assertk.assertions.startsWith
import dev.hsbrysk.kuery.core.list
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.BeanInstantiationException
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter

/**
 * Kotlin value classes are NOT supported on the fetch side, in either position:
 *
 * - As the fetch type itself, DataClassRowMapper cannot resolve the parameter names of the
 *   value class's (private, boxed) constructor.
 * - As a data class property, instantiation fails because the enclosing class's JVM
 *   constructor takes the unboxed underlying type; even a registered reading converter does
 *   not help.
 *
 * Use the underlying type and wrap it yourself, or a regular data class. This pins the
 * current limitation.
 */
class ValueClassFetchTest {
    @JvmInline
    value class UserName(val value: String)

    private val kueryClient = h2.kueryClient()

    @BeforeEach
    fun beforeEach() {
        h2.setUpForConverterTest()
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES ('user1'), ('user2')").update()
    }

    @AfterEach
    fun afterEach() {
        h2.tearDownForConverterTest()
    }

    @Test
    fun `value class is not supported as a fetch type`() {
        assertFailure {
            kueryClient.sql {
                +"SELECT text AS value FROM converter ORDER BY id"
            }.list<UserName>()
        }.isInstanceOf(IllegalStateException::class)
            .message().isNotNull().startsWith("Cannot resolve parameter names")
    }

    @Test
    fun `value class as a data class property is not supported either`() {
        // given
        val kueryClient = h2.kueryClient(listOf(StringToUserNameConverter()))

        // when & then
        assertFailure {
            kueryClient.sql {
                +"SELECT id, text FROM converter ORDER BY id"
            }.list<Record>()
        }.isInstanceOf(BeanInstantiationException::class)
    }

    data class Record(
        val id: Long,
        val text: UserName,
    )

    @ReadingConverter
    class StringToUserNameConverter : Converter<String, UserName> {
        override fun convert(source: String): UserName = UserName(source)
    }

    companion object {
        private val h2 = H2TestDatabase()
    }
}
