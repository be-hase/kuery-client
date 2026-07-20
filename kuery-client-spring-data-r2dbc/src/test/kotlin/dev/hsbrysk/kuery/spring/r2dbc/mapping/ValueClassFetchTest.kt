package dev.hsbrysk.kuery.spring.r2dbc.mapping

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.messageContains
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
import java.math.BigDecimal

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

    @JvmInline
    value class Secret private constructor(val value: String) {
        companion object {
            fun of(value: String): Secret = Secret(value)
        }
    }

    @JvmInline
    value class Chars(val value: String) : CharSequence {
        override val length: Int get() = value.length

        override fun get(index: Int): Char = value[index]

        override fun subSequence(
            startIndex: Int,
            endIndex: Int,
        ): CharSequence = value.subSequence(startIndex, endIndex)
    }

    @JvmInline
    value class Wrapped<T>(val value: T)

    @JvmInline
    value class Money(val value: BigDecimal)

    @JvmInline
    value class Wrapper(val value: UserName)

    @JvmInline
    value class StringId(val value: String)

    @JvmInline
    value class Amount(val value: Int)

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
    fun `default value applies when the column for a defaulted parameter is SQL NULL`() = runTest {
        // Mirrors Spring's DataClassRowMapper (BeanUtils.instantiateClass omits null arguments
        // for optional parameters, so Kotlin default values apply).
        // given
        insert("INSERT INTO converter (text) VALUES ('user1')")

        // when & then
        val record: DefaultedRecord = kueryClient.sql {
            +"SELECT text, CAST(NULL AS VARCHAR) AS opt FROM converter"
        }.single()
        assertThat(record.opt).isEqualTo("fallback")
    }

    @Test
    fun `mutable body property is populated from a matching column`() = runTest {
        // Mirrors Spring's DataClassRowMapper, which populates non-constructor properties
        // through setters after constructing the instance.
        // given
        insert("INSERT INTO converter (text) VALUES ('user1')")

        // when & then
        val record: MutableRecord = kueryClient.sql {
            +"SELECT text, 'e' AS extra FROM converter"
        }.single()
        assertThat(record.extra).isEqualTo("e")
    }

    @Test
    fun `value class with a private constructor is mapped through its constructor`() = runTest {
        // Parity with Spring's mappers, which make non-public constructors accessible.
        // given
        insert("INSERT INTO converter (text) VALUES ('user1')")

        // when & then
        val result: Secret = kueryClient.sql {
            +"SELECT text FROM converter"
        }.single()
        assertThat(result).isEqualTo(Secret.of("user1"))
    }

    @Test
    fun `value class implementing an interface is mapped as a fetch type`() = runTest {
        // A CharSequence-implementing value class must not be misrouted to the simple-type path.
        // given
        insert("INSERT INTO converter (text) VALUES ('user1')")

        // when & then
        val result: Chars = kueryClient.sql {
            +"SELECT text FROM converter"
        }.single()
        assertThat(result).isEqualTo(Chars("user1"))
    }

    @Test
    fun `generic value class is rejected with a clear error`() = runTest {
        // The underlying type is a type parameter, so automatic boxing cannot know the intended
        // runtime type; fail fast instead of constructing a heap-polluted instance.
        // given
        insert("INSERT INTO converter (text) VALUES ('user1')")

        // when & then
        assertFailure {
            kueryClient.sql {
                +"SELECT text FROM converter"
            }.single(GenericRecord::class)
        }.isInstanceOf(IllegalArgumentException::class)
            .messageContains("generic value class")
    }

    @Test
    fun `nested value class is mapped recursively`() = runTest {
        // given
        insert("INSERT INTO converter (text) VALUES ('user1')")

        // when & then
        val record: NestedRecord = kueryClient.sql {
            +"SELECT text FROM converter"
        }.single()
        assertThat(record.text).isEqualTo(Wrapper(UserName("user1")))
    }

    @Test
    fun `generic value class is mapped through a registered reading converter`() = runTest {
        // A generic value class cannot be boxed automatically, but a registered converter wins
        // over boxing and must still be consulted (it is not defeated by the boxing fail-fast).
        // given
        val kueryClient = h2.kueryClient(listOf(StringToWrappedConverter()))
        insert("INSERT INTO converter (text) VALUES ('user1')")

        // when & then
        val record: GenericRecord = kueryClient.sql {
            +"SELECT text FROM converter"
        }.single()
        assertThat(record.text).isEqualTo(Wrapped("user1"))
    }

    @Test
    fun `a reading converter from the raw column type wins over coercion to the underlying type`() = runTest {
        // The BIGINT id column is retrieved raw as Long, so a Converter<Long, StringId> is applied
        // before the driver could coerce the value to the underlying String.
        // given
        val kueryClient = h2.kueryClient(listOf(LongToStringIdConverter()))
        insert("INSERT INTO converter (text) VALUES ('x')")

        // when & then
        val result: StringId = kueryClient.sql {
            +"SELECT id FROM converter"
        }.single()
        assertThat(result).isEqualTo(StringId("custom:1"))
    }

    // NOTE: the jdbc counterpart `driver coercion remains available when no reading converter
    // matches` (BIGINT -> Boolean via ResultSet.getBoolean) is jdbc-only: r2dbc-h2's
    // `readable.get(index, Boolean)` does not coerce a numeric column to Boolean, so that specific
    // value class was never mappable on r2dbc-h2. The driver-typed retrieval path itself is still
    // exercised on r2dbc by the coercion tests here (the raw type differs from the underlying).

    @Test
    fun `value class underlying type is coerced from the raw column type when no converter matches`() = runTest {
        // The BIGINT id is retrieved raw as Long and coerced to the Int underlying via the
        // ConversionService (no converter targets Amount).
        // given
        insert("INSERT INTO converter (text) VALUES ('x')")

        // when & then
        val result: Amount = kueryClient.sql {
            +"SELECT id FROM converter"
        }.single()
        assertThat(result).isEqualTo(Amount(1))
    }

    @Test
    fun `a reading converter maps a column the underlying type cannot read`() = runTest {
        // The underlying type (BigDecimal) cannot read the VARCHAR value directly; the raw value
        // must still reach the registered converter targeting the value class.
        // given
        val kueryClient = h2.kueryClient(listOf(StringToMoneyConverter()))
        insert("INSERT INTO converter (text) VALUES ('\$10.00')")

        // when & then
        val result: Money = kueryClient.sql {
            +"SELECT text FROM converter"
        }.single()
        assertThat(result).isEqualTo(Money(BigDecimal("10.00")))
    }

    @Test
    fun `nullable value class property maps to null when the underlying converter returns null`() = runTest {
        // A converter for the underlying type may return null (the Converter contract allows it);
        // the value class must map to null rather than being boxed around null.
        // given
        val kueryClient = h2.kueryClient(listOf(BlankToNullBigDecimalConverter()))
        insert("INSERT INTO converter (text) VALUES ('')")

        // when & then
        val record: NullableMoneyRecord = kueryClient.sql {
            +"SELECT text FROM converter"
        }.single()
        assertThat(record.text).isNull()
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

    data class DefaultedRecord(
        val text: UserName,
        val opt: String = "fallback",
    )

    data class MutableRecord(val text: UserName) {
        var extra: String? = null
    }

    data class GenericRecord(val text: Wrapped<String>)

    data class NestedRecord(val text: Wrapper)

    data class NullableMoneyRecord(val text: Money?)

    @ReadingConverter
    class StringToUserNameConverter : Converter<String, UserName> {
        override fun convert(source: String): UserName = UserName("custom:$source")
    }

    @ReadingConverter
    class StringToWrappedConverter : Converter<String, Wrapped<String>> {
        override fun convert(source: String): Wrapped<String> = Wrapped(source)
    }

    @ReadingConverter
    class StringToMoneyConverter : Converter<String, Money> {
        override fun convert(source: String): Money = Money(BigDecimal(source.removePrefix("$")))
    }

    @ReadingConverter
    class BlankToNullBigDecimalConverter : Converter<String, BigDecimal?> {
        override fun convert(source: String): BigDecimal? = source.ifBlank { null }?.let { BigDecimal(it) }
    }

    @ReadingConverter
    class LongToStringIdConverter : Converter<Long, StringId> {
        override fun convert(source: Long): StringId = StringId("custom:$source")
    }

    companion object {
        private val h2 = H2TestDatabase()
    }
}
