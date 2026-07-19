package dev.hsbrysk.kuery.spring.r2dbc.mapping

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import dev.hsbrysk.kuery.core.list
import dev.hsbrysk.kuery.core.single
import dev.hsbrysk.kuery.spring.r2dbc.H2TestDatabase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.convert.converter.Converter
import org.springframework.dao.DataRetrievalFailureException
import org.springframework.data.convert.ReadingConverter
import org.springframework.r2dbc.core.awaitRowsUpdated

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
    fun beforeEach() = runTest {
        h2.setUpForConverterTest()
    }

    @AfterEach
    fun afterEach() = runTest {
        h2.tearDownForConverterTest()
    }

    private suspend fun insert(sql: String) {
        h2.databaseClient.sql(sql).fetch().awaitRowsUpdated()
    }

    @Test
    fun `value class is mapped when fetched as a scalar`() = runTest {
        // given
        insert("INSERT INTO converter (text) VALUES ('user1'), ('user2')")

        // when & then
        val result: List<UserName> = kueryClient.sql {
            +"SELECT text FROM converter ORDER BY id"
        }.list()
        assertThat(result).isEqualTo(listOf(UserName("user1"), UserName("user2")))
    }

    @Test
    fun `value class scalar keeps SQL NULL as a null element`() = runTest {
        // given
        insert("INSERT INTO converter (text) VALUES ('user1'), (NULL)")

        // when & then
        val result: List<UserName?> = kueryClient.sql {
            +"SELECT text FROM converter ORDER BY id"
        }.list<UserName>()
        assertThat(result).isEqualTo(listOf(UserName("user1"), null))
    }

    @Test
    fun `value class as a data class property is mapped from the underlying column`() = runTest {
        // given
        insert("INSERT INTO converter (text) VALUES ('user1')")

        // when & then
        val record: Record = kueryClient.sql {
            +"SELECT id, text FROM converter"
        }.single()
        assertThat(record.text).isEqualTo(UserName("user1"))
    }

    @Test
    fun `nullable value class property maps SQL NULL to null`() = runTest {
        // given
        insert("INSERT INTO converter (text) VALUES (NULL)")

        // when & then
        val record: NullableRecord = kueryClient.sql {
            +"SELECT id, text FROM converter"
        }.single()
        assertThat(record.text).isNull()
    }

    @Test
    fun `value class property wrapping an enum is mapped`() = runTest {
        // given
        insert("INSERT INTO converter (text) VALUES ('HOGE')")

        // when & then
        val record: EnumRecord = kueryClient.sql {
            +"SELECT text FROM converter"
        }.single()
        assertThat(record.text).isEqualTo(Status(SampleEnum.HOGE))
    }

    @Test
    fun `init validation runs when mapping a value class`() = runTest {
        // given
        insert("INSERT INTO converter (text) VALUES ('')")

        // when & then
        assertFailure {
            kueryClient.sql {
                +"SELECT text FROM converter"
            }.list<ValidatedName>()
        }.isInstanceOf(IllegalArgumentException::class)
            .hasMessage("must not be empty")
    }

    @Test
    fun `reading converter takes precedence over automatic boxing`() = runTest {
        // given
        val kueryClient = h2.kueryClient(listOf(StringToUserNameConverter()))
        insert("INSERT INTO converter (text) VALUES ('user1')")

        // when & then
        val record: Record = kueryClient.sql {
            +"SELECT id, text FROM converter"
        }.single()
        assertThat(record.text).isEqualTo(UserName("custom:user1"))
    }

    @Test
    fun `snake_case column maps to a camelCase value class property`() = runTest {
        // given
        insert("INSERT INTO converter (text) VALUES ('user1')")

        // when & then
        val record: AliasedRecord = kueryClient.sql {
            +"SELECT text AS user_name FROM converter"
        }.single()
        assertThat(record.userName).isEqualTo(UserName("user1"))
    }

    @Test
    fun `a missing column for a constructor parameter throws even if the property is nullable`() = runTest {
        // Same exception as the plain data class path (DataClassColumnMismatchTest). The jdbc
        // module reports BadSqlGrammarException instead.
        // given
        insert("INSERT INTO converter (text) VALUES ('user1')")

        // when & then
        assertFailure {
            kueryClient.sql {
                +"SELECT text FROM converter"
            }.single(MismatchRecord::class)
        }.isInstanceOf(DataRetrievalFailureException::class)
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
