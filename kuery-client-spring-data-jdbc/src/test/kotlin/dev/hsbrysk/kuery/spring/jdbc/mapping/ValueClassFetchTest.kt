package dev.hsbrysk.kuery.spring.jdbc.mapping

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import dev.hsbrysk.kuery.core.list
import dev.hsbrysk.kuery.core.single
import dev.hsbrysk.kuery.spring.jdbc.H2TestDatabase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.jdbc.BadSqlGrammarException
import org.springframework.jdbc.IncorrectResultSetColumnCountException

/**
 * Fetch-side (read) support for Kotlin value classes, in both positions: as the fetch type
 * itself and as a data class constructor property. A column value is converted to the
 * underlying type and boxed through the primary constructor (running `init` validation);
 * a registered reading converter takes precedence over automatic boxing.
 */
class ValueClassFetchTest {
    @JvmInline
    value class UserName(val value: String)

    @JvmInline
    value class ValidatedName(val value: String) {
        init {
            require(value.isNotEmpty()) { "must not be empty" }
        }
    }

    enum class SampleEnum {
        HOGE,
    }

    @JvmInline
    value class Status(val value: SampleEnum)

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
    fun `value class is mapped when fetched as a scalar`() {
        // given
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES ('user1'), ('user2')").update()

        // when & then
        val result: List<UserName> = kueryClient.sql {
            +"SELECT text FROM converter ORDER BY id"
        }.list()
        assertThat(result).isEqualTo(listOf(UserName("user1"), UserName("user2")))
    }

    @Test
    fun `value class scalar keeps SQL NULL as a null element`() {
        // given
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES ('user1'), (NULL)").update()

        // when & then
        val result: List<UserName?> = kueryClient.sql {
            +"SELECT text FROM converter ORDER BY id"
        }.list<UserName>()
        assertThat(result).isEqualTo(listOf(UserName("user1"), null))
    }

    @Test
    fun `fetching a value class scalar from a multi-column result fails`() {
        // Same contract as the simple scalar path (SingleColumnRowMapper). The r2dbc scalar
        // path does not validate the column count, so this test is jdbc-only.
        // given
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES ('user1')").update()

        // when & then
        assertFailure {
            kueryClient.sql {
                +"SELECT id, text FROM converter"
            }.list<UserName>()
        }.isInstanceOf(IncorrectResultSetColumnCountException::class)
    }

    @Test
    fun `value class as a data class property is mapped from the underlying column`() {
        // given
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES ('user1')").update()

        // when & then
        val record: Record = kueryClient.sql {
            +"SELECT id, text FROM converter"
        }.single()
        assertThat(record.text).isEqualTo(UserName("user1"))
    }

    @Test
    fun `nullable value class property maps SQL NULL to null`() {
        // given
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES (NULL)").update()

        // when & then
        val record: NullableRecord = kueryClient.sql {
            +"SELECT id, text FROM converter"
        }.single()
        assertThat(record.text).isNull()
    }

    @Test
    fun `value class property wrapping an enum is mapped`() {
        // given
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES ('HOGE')").update()

        // when & then
        val record: EnumRecord = kueryClient.sql {
            +"SELECT text FROM converter"
        }.single()
        assertThat(record.text).isEqualTo(Status(SampleEnum.HOGE))
    }

    @Test
    fun `init validation runs when mapping a value class`() {
        // given
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES ('')").update()

        // when & then
        assertFailure {
            kueryClient.sql {
                +"SELECT text FROM converter"
            }.list<ValidatedName>()
        }.isInstanceOf(IllegalArgumentException::class)
            .hasMessage("must not be empty")
    }

    @Test
    fun `reading converter takes precedence over automatic boxing`() {
        // given
        val kueryClient = h2.kueryClient(listOf(StringToUserNameConverter()))
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES ('user1')").update()

        // when & then
        val record: Record = kueryClient.sql {
            +"SELECT id, text FROM converter"
        }.single()
        assertThat(record.text).isEqualTo(UserName("custom:user1"))
    }

    @Test
    fun `snake_case column maps to a camelCase value class property`() {
        // given
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES ('user1')").update()

        // when & then
        val record: AliasedRecord = kueryClient.sql {
            +"SELECT text AS user_name FROM converter"
        }.single()
        assertThat(record.userName).isEqualTo(UserName("user1"))
    }

    @Test
    fun `a missing column for a constructor parameter throws even if the property is nullable`() {
        // Same exception as the plain data class path (DataClassColumnMismatchTest): the column
        // is resolved via ResultSet.findColumn and the driver error is translated to
        // BadSqlGrammarException. The r2dbc module reports DataRetrievalFailureException instead.
        // given
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES ('user1')").update()

        // when & then
        assertFailure {
            kueryClient.sql {
                +"SELECT text FROM converter"
            }.single(MismatchRecord::class)
        }.isInstanceOf(BadSqlGrammarException::class)
    }

    data class Record(
        val id: Long,
        val text: UserName,
    )

    data class NullableRecord(
        val id: Long,
        val text: UserName?,
    )

    data class EnumRecord(val text: Status)

    data class AliasedRecord(val userName: UserName)

    data class MismatchRecord(
        val text: UserName,
        val nickname: String?,
    )

    @ReadingConverter
    class StringToUserNameConverter : Converter<String, UserName> {
        override fun convert(source: String): UserName = UserName("custom:$source")
    }

    companion object {
        private val h2 = H2TestDatabase()
    }
}
