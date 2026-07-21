package dev.hsbrysk.kuery.spring.jdbc.mapping

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.messageContains
import dev.hsbrysk.kuery.core.list
import dev.hsbrysk.kuery.core.sequence
import dev.hsbrysk.kuery.core.single
import dev.hsbrysk.kuery.core.singleOrNull
import dev.hsbrysk.kuery.spring.jdbc.H2TestDatabase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.convert.converter.Converter
import org.springframework.dao.TypeMismatchDataAccessException
import org.springframework.data.convert.ReadingConverter
import org.springframework.jdbc.BadSqlGrammarException
import org.springframework.jdbc.IncorrectResultSetColumnCountException
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

    @JvmInline
    value class Flag(val value: Boolean)

    @JvmInline
    value class OptionalUserName(val value: String?)

    @JvmInline
    value class Token(val value: String) {
        init {
            require(value.isNotEmpty()) { "must not be empty" }
        }

        // A secondary constructor means the class exposes more than one constructor-impl, so the
        // fast box path is unavailable and boxing falls back to the kotlin-reflect constructor.
        constructor(number: Int) : this(number.toString())
    }

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
    fun `nullable-underlying value class scalar keeps SQL NULL as an inner null`() {
        // The underlying type is nullable, so SQL NULL is taken into the value class as an inner
        // null (OptionalUserName(null)), mirroring the write side which binds such a value as SQL
        // NULL. This differs from the non-null-underlying scalar, which maps SQL NULL to a null
        // element.
        // given
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES ('user1'), (NULL)").update()

        // when & then
        val result: List<OptionalUserName> = kueryClient.sql {
            +"SELECT text FROM converter ORDER BY id"
        }.list()
        assertThat(result).isEqualTo(listOf(OptionalUserName("user1"), OptionalUserName(null)))
    }

    @Test
    fun `sequence keeps a SQL NULL value class scalar as a null element`() {
        // The scalar mapper maps a SQL NULL row to a null element; sequence() must preserve it
        // (non-null underlying type), like list().
        // given
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES ('user1'), (NULL)").update()

        // when & then
        val result: List<UserName?> = kueryClient.sql {
            +"SELECT text FROM converter ORDER BY id"
        }.sequence<UserName>().toList()
        assertThat(result).isEqualTo(listOf(UserName("user1"), null))
    }

    @Test
    fun `sequence maps a nullable-underlying value class scalar SQL NULL to an inner null`() {
        // given
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES ('user1'), (NULL)").update()

        // when & then
        val result: List<OptionalUserName> = kueryClient.sql {
            +"SELECT text FROM converter ORDER BY id"
        }.sequence<OptionalUserName>().toList()
        assertThat(result).isEqualTo(listOf(OptionalUserName("user1"), OptionalUserName(null)))
    }

    @Test
    fun `singleOrNull maps a SQL NULL value class scalar to null`() {
        // The scalar mapper maps the SQL NULL row to a null element; singleOrNull() must return it
        // as null (non-null underlying type).
        // given
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES (NULL)").update()

        // when & then
        val result: UserName? = kueryClient.sql {
            +"SELECT text FROM converter"
        }.singleOrNull()
        assertThat(result).isNull()
    }

    @Test
    fun `singleOrNull maps a nullable-underlying value class scalar SQL NULL to an inner null`() {
        // given
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES (NULL)").update()

        // when & then
        val result: OptionalUserName? = kueryClient.sql {
            +"SELECT text FROM converter"
        }.singleOrNull()
        assertThat(result).isEqualTo(OptionalUserName(null))
    }

    @Test
    fun `singleOrNull returns a value class scalar when a row matches`() {
        // given
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES ('user1')").update()

        // when & then
        val result: UserName? = kueryClient.sql {
            +"SELECT text FROM converter"
        }.singleOrNull()
        assertThat(result).isEqualTo(UserName("user1"))
    }

    @Test
    fun `singleOrNull returns null for a nullable-underlying value class scalar when no row matches`() {
        // No row at all maps to the outer null, distinct from a SQL NULL row of a nullable-underlying
        // value class, which maps to OptionalUserName(null).
        // when & then
        val result: OptionalUserName? = kueryClient.sql {
            +"SELECT text FROM converter WHERE text = 'absent'"
        }.singleOrNull()
        assertThat(result).isNull()
    }

    @Test
    fun `single rejects a SQL NULL value class scalar with a non-null underlying`() {
        // The underlying type is non-null, so a SQL NULL cannot be held in the value class; single()
        // must fail rather than hand back a null under a non-null return type. (singleOrNull()
        // tolerates it and returns null; see the test above.)
        // given
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES (NULL)").update()

        // when & then
        assertFailure {
            kueryClient.sql {
                +"SELECT text FROM converter"
            }.single<UserName>()
        }.isInstanceOf(TypeMismatchDataAccessException::class)
    }

    @Test
    fun `non-nullable value class property rejects SQL NULL`() {
        // The property and its underlying type are both non-null, so a SQL NULL cannot be taken into
        // the value class; construction fails rather than yielding a null value class.
        // given
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES (NULL)").update()

        // when & then
        // Kotlin's non-null parameter intrinsic in the data class constructor rejects the null,
        // the same failure the plain data class path produces for a non-null property.
        assertFailure {
            kueryClient.sql {
                +"SELECT text FROM converter"
            }.single(NameRecord::class)
        }.isInstanceOf(NullPointerException::class)
    }

    @Test
    fun `value class with a secondary constructor is boxed through its primary constructor`() {
        // A secondary constructor leaves no single constructor-impl/box-impl pair, so boxing falls
        // back to the kotlin-reflect primary constructor rather than the fast path.
        // given
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES ('user1')").update()

        // when & then
        val result: Token = kueryClient.sql {
            +"SELECT text FROM converter"
        }.single()
        assertThat(result).isEqualTo(Token("user1"))
    }

    @Test
    fun `init validation surfaces as the original exception for a value class with a secondary constructor`() {
        // The kotlin-reflect constructor path wraps init failures in InvocationTargetException; it
        // must be unwrapped so the require() IllegalArgumentException surfaces directly.
        // given
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES ('')").update()

        // when & then
        assertFailure {
            kueryClient.sql {
                +"SELECT text FROM converter"
            }.list<Token>()
        }.isInstanceOf(IllegalArgumentException::class)
            .hasMessage("must not be empty")
    }

    @Test
    fun `generic value class is rejected as a scalar with a clear error`() {
        // Same fail-fast as the data class property position, but with the generic value class as the
        // fetch type itself: its underlying is a type parameter, so it cannot be boxed automatically.
        // given
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES ('user1')").update()

        // when & then
        assertFailure {
            kueryClient.sql {
                +"SELECT text FROM converter"
            }.list<Wrapped<String>>()
        }.isInstanceOf(IllegalArgumentException::class)
            .messageContains("generic value class")
    }

    @Test
    fun `generic value class scalar is mapped through a registered reading converter`() {
        // A generic value class cannot be boxed automatically, but a registered converter serves it
        // as the fetch type itself (it wins before the boxing fail-fast).
        // given
        val kueryClient = h2.kueryClient(listOf(StringToWrappedConverter()))
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES ('user1')").update()

        // when & then
        val result: Wrapped<String> = kueryClient.sql {
            +"SELECT text FROM converter"
        }.single()
        assertThat(result).isEqualTo(Wrapped("user1"))
    }

    @Test
    fun `generic value class scalar keeps SQL NULL as a null element`() {
        // A generic value class has no known underlying type, so it cannot take a SQL NULL as an
        // inner null; the SQL NULL row is kept as a null element, like a non-null-underlying scalar.
        // given
        val kueryClient = h2.kueryClient(listOf(StringToWrappedConverter()))
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES ('user1'), (NULL)").update()

        // when & then
        val result: List<Wrapped<String>?> = kueryClient.sql {
            +"SELECT text FROM converter ORDER BY id"
        }.list<Wrapped<String>>()
        assertThat(result).isEqualTo(listOf(Wrapped("user1"), null))
    }

    @Test
    fun `nullable-underlying value class property maps SQL NULL to an inner null`() {
        // The property is non-null but its underlying type is nullable, so SQL NULL is taken into
        // the value class as OptionalUserName(null) rather than failing.
        // given
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES (NULL)").update()

        // when & then
        val record: OptRecord = kueryClient.sql {
            +"SELECT text FROM converter"
        }.single()
        assertThat(record.text).isEqualTo(OptionalUserName(null))
    }

    @Test
    fun `doubly nullable value class property maps SQL NULL to the outer null`() {
        // Both the property and the underlying type are nullable, so SQL NULL cannot be resolved
        // unambiguously; it maps to the outer null (property is null), not OptionalUserName(null).
        // given
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES (NULL)").update()

        // when & then
        val record: OptNullableRecord = kueryClient.sql {
            +"SELECT text FROM converter"
        }.single()
        assertThat(record.text).isNull()
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
    fun `default value applies when the column for a defaulted parameter is SQL NULL`() {
        // Mirrors Spring's DataClassRowMapper (BeanUtils.instantiateClass omits null arguments
        // for optional parameters, so Kotlin default values apply).
        // given
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES ('user1')").update()

        // when & then
        val record: DefaultedRecord = kueryClient.sql {
            +"SELECT text, CAST(NULL AS VARCHAR) AS opt FROM converter"
        }.single()
        assertThat(record.opt).isEqualTo("fallback")
    }

    @Test
    fun `mutable body property is populated from a matching column`() {
        // Mirrors Spring's DataClassRowMapper, which populates non-constructor properties
        // through setters after constructing the instance.
        // given
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES ('user1')").update()

        // when & then
        val record: MutableRecord = kueryClient.sql {
            +"SELECT text, 'e' AS extra FROM converter"
        }.single()
        assertThat(record.extra).isEqualTo("e")
    }

    @Test
    fun `value class with a private constructor is mapped through its constructor`() {
        // Parity with Spring's mappers, which make non-public constructors accessible.
        // given
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES ('user1')").update()

        // when & then
        val result: Secret = kueryClient.sql {
            +"SELECT text FROM converter"
        }.single()
        assertThat(result).isEqualTo(Secret.of("user1"))
    }

    @Test
    fun `value class implementing an interface is mapped as a fetch type`() {
        // A CharSequence-implementing value class must not be misrouted to the simple-type path.
        // given
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES ('user1')").update()

        // when & then
        val result: Chars = kueryClient.sql {
            +"SELECT text FROM converter"
        }.single()
        assertThat(result).isEqualTo(Chars("user1"))
    }

    @Test
    fun `generic value class is rejected with a clear error`() {
        // The underlying type is a type parameter, so automatic boxing cannot know the intended
        // runtime type; fail fast instead of constructing a heap-polluted instance.
        // given
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES ('user1')").update()

        // when & then
        assertFailure {
            kueryClient.sql {
                +"SELECT text FROM converter"
            }.single(GenericRecord::class)
        }.isInstanceOf(IllegalArgumentException::class)
            .messageContains("generic value class")
    }

    @Test
    fun `nested value class is mapped recursively`() {
        // given
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES ('user1')").update()

        // when & then
        val record: NestedRecord = kueryClient.sql {
            +"SELECT text FROM converter"
        }.single()
        assertThat(record.text).isEqualTo(Wrapper(UserName("user1")))
    }

    @Test
    fun `generic value class is mapped through a registered reading converter`() {
        // A generic value class cannot be boxed automatically, but a registered converter wins
        // over boxing and must still be consulted (it is not defeated by the boxing fail-fast).
        // given
        val kueryClient = h2.kueryClient(listOf(StringToWrappedConverter()))
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES ('user1')").update()

        // when & then
        val record: GenericRecord = kueryClient.sql {
            +"SELECT text FROM converter"
        }.single()
        assertThat(record.text).isEqualTo(Wrapped("user1"))
    }

    @Test
    fun `a reading converter from the raw column type wins over coercion to the underlying type`() {
        // The BIGINT id column is retrieved raw as Long, so a Converter<Long, StringId> is applied
        // before the driver could coerce the value to the underlying String.
        // given
        val kueryClient = h2.kueryClient(listOf(LongToStringIdConverter()))
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES ('x')").update()

        // when & then
        val result: StringId = kueryClient.sql {
            +"SELECT id FROM converter"
        }.single()
        assertThat(result).isEqualTo(StringId("custom:1"))
    }

    @Test
    fun `driver coercion remains available when no reading converter matches`() {
        // The ConversionService has no Long -> Boolean converter; the driver's typed retrieval
        // (ResultSet.getBoolean) must still coerce the BIGINT value to the Boolean underlying.
        // jdbc-only: r2dbc-h2 does not coerce a numeric column to Boolean via readable.get.
        // given
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES ('x')").update()

        // when & then
        val result: Flag = kueryClient.sql {
            +"SELECT id FROM converter"
        }.single()
        assertThat(result).isEqualTo(Flag(true))
    }

    @Test
    fun `a reading converter from the driver typed value wins over automatic boxing`() {
        // The BIGINT id has no Converter<Long, StringId>, so it is retrieved typed as the String
        // underlying (ResultSet.getString); a Converter<String, StringId> keyed on that produced
        // type must still win over automatic boxing (which would yield StringId("1")).
        // jdbc-only: r2dbc-h2 does not coerce a numeric column to a String via readable.get, so the
        // driver never produces the typed String there (same limitation as the Boolean case above).
        // given
        val kueryClient = h2.kueryClient(listOf(StringToStringIdConverter()))
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES ('x')").update()

        // when & then
        val result: StringId = kueryClient.sql {
            +"SELECT id FROM converter"
        }.single()
        assertThat(result).isEqualTo(StringId("typed:1"))
    }

    @Test
    fun `value class underlying type is coerced from the raw column type when no converter matches`() {
        // The BIGINT id is retrieved raw as Long and coerced to the Int underlying via the
        // ConversionService (no converter targets Amount).
        // given
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES ('x')").update()

        // when & then
        val result: Amount = kueryClient.sql {
            +"SELECT id FROM converter"
        }.single()
        assertThat(result).isEqualTo(Amount(1))
    }

    @Test
    fun `a reading converter maps a column the underlying type cannot read`() {
        // The underlying type (BigDecimal) cannot read the VARCHAR value directly; the raw value
        // must still reach the registered converter targeting the value class.
        // given
        val kueryClient = h2.kueryClient(listOf(StringToMoneyConverter()))
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES ('\$10.00')").update()

        // when & then
        val result: Money = kueryClient.sql {
            +"SELECT text FROM converter"
        }.single()
        assertThat(result).isEqualTo(Money(BigDecimal("10.00")))
    }

    @Test
    fun `nullable value class property maps to null when the underlying converter returns null`() {
        // A converter for the underlying type may return null (the Converter contract allows it);
        // the value class must map to null rather than being boxed around null.
        // given
        val kueryClient = h2.kueryClient(listOf(BlankToNullBigDecimalConverter()))
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES ('')").update()

        // when & then
        val record: NullableMoneyRecord = kueryClient.sql {
            +"SELECT text FROM converter"
        }.single()
        assertThat(record.text).isNull()
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

    @Test
    fun `value class mutable body property is populated when no constructor parameter is a value class`() {
        // The constructor takes no value class parameter, so mapping stays on Spring's
        // DataClassRowMapper; a value class mutable body property is still populated through the
        // inherited setter + ConversionService path.
        // given
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES ('user1')").update()

        // when & then
        val record: BodyValueClassRecord = kueryClient.sql {
            +"SELECT id, text AS name FROM converter"
        }.single()
        assertThat(record.name).isEqualTo(UserName("user1"))
    }

    @Test
    fun `value class mutable body property is populated alongside a value class constructor parameter`() {
        // A value class constructor parameter routes to ValueClassPropertyRowMapper; a value class
        // mutable body property is still populated through the inherited setter path.
        // given
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES ('user1')").update()

        // when & then
        val record: ConstructorAndBodyValueClassRecord = kueryClient.sql {
            +"SELECT text, text AS name FROM converter"
        }.single()
        assertThat(record.text).isEqualTo(UserName("user1"))
        assertThat(record.name).isEqualTo(UserName("user1"))
    }

    data class BodyValueClassRecord(val id: Long) {
        var name: UserName? = null
    }

    data class ConstructorAndBodyValueClassRecord(val text: UserName) {
        var name: UserName? = null
    }

    data class NameRecord(val text: UserName)

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

    data class OptRecord(val text: OptionalUserName)

    data class OptNullableRecord(val text: OptionalUserName?)

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

    @ReadingConverter
    class StringToStringIdConverter : Converter<String, StringId> {
        override fun convert(source: String): StringId = StringId("typed:$source")
    }

    companion object {
        private val h2 = H2TestDatabase()
    }
}
