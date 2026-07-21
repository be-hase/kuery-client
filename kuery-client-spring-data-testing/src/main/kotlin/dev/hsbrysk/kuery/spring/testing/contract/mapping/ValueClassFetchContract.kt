package dev.hsbrysk.kuery.spring.testing.contract.mapping

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.messageContains
import dev.hsbrysk.kuery.core.flow
import dev.hsbrysk.kuery.core.list
import dev.hsbrysk.kuery.core.single
import dev.hsbrysk.kuery.core.singleOrNull
import dev.hsbrysk.kuery.spring.testing.ExceptionProfile
import dev.hsbrysk.kuery.spring.testing.contract.conversion.ConverterContractBase
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.core.convert.converter.Converter
import org.springframework.dao.TypeMismatchDataAccessException
import org.springframework.data.convert.ReadingConverter
import java.math.BigDecimal

/**
 * Fetch-side (read) support for Kotlin value classes, in both positions: as the fetch type
 * itself and as a data class constructor property. A column value is converted to the
 * underlying type and boxed through the primary constructor (running `init` validation);
 * a registered reading converter takes precedence over automatic boxing.
 *
 * Contract shared by the jdbc and r2dbc modules. Each module runs it via a concrete subclass
 * that supplies its `ContractDatabase`.
 */
@Suppress("TooManyFunctions")
abstract class ValueClassFetchContract : ConverterContractBase() {
    protected abstract val exceptionProfile: ExceptionProfile

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

    @Test
    fun `value class is mapped when fetched as a scalar`() = runTest {
        // given
        database.execute("INSERT INTO converter (text) VALUES ('user1'), ('user2')")

        // when & then
        val result: List<UserName> = kueryClient.sql {
            +"SELECT text FROM converter ORDER BY id"
        }.list()
        assertThat(result).isEqualTo(listOf(UserName("user1"), UserName("user2")))
    }

    @Test
    fun `value class scalar keeps SQL NULL as a null element`() = runTest {
        // given
        database.execute("INSERT INTO converter (text) VALUES ('user1'), (NULL)")

        // when & then
        val result: List<UserName?> = kueryClient.sql {
            +"SELECT text FROM converter ORDER BY id"
        }.list<UserName>()
        assertThat(result).isEqualTo(listOf(UserName("user1"), null))
    }

    @Test
    fun `nullable-underlying value class scalar keeps SQL NULL as an inner null`() = runTest {
        // The underlying type is nullable, so SQL NULL is taken into the value class as an inner
        // null (OptionalUserName(null)), mirroring the write side which binds such a value as SQL
        // NULL. This differs from the non-null-underlying scalar, which maps SQL NULL to a null
        // element.
        // given
        database.execute("INSERT INTO converter (text) VALUES ('user1'), (NULL)")

        // when & then
        val result: List<OptionalUserName> = kueryClient.sql {
            +"SELECT text FROM converter ORDER BY id"
        }.list()
        assertThat(result).isEqualTo(listOf(OptionalUserName("user1"), OptionalUserName(null)))
    }

    @Test
    fun `flow keeps a SQL NULL value class scalar as a null element`() = runTest {
        // The scalar mapper maps a SQL NULL row to a null element; flow() must preserve it
        // (non-null underlying type), like list().
        // given
        database.execute("INSERT INTO converter (text) VALUES ('user1'), (NULL)")

        // when & then
        val result: List<UserName?> = kueryClient.sql {
            +"SELECT text FROM converter ORDER BY id"
        }.flow<UserName>().toList()
        assertThat(result).isEqualTo(listOf(UserName("user1"), null))
    }

    @Test
    fun `flow maps a nullable-underlying value class scalar SQL NULL to an inner null`() = runTest {
        // given
        database.execute("INSERT INTO converter (text) VALUES ('user1'), (NULL)")

        // when & then
        val result: List<OptionalUserName> = kueryClient.sql {
            +"SELECT text FROM converter ORDER BY id"
        }.flow<OptionalUserName>().toList()
        assertThat(result).isEqualTo(listOf(OptionalUserName("user1"), OptionalUserName(null)))
    }

    @Test
    fun `singleOrNull maps a SQL NULL value class scalar to null`() = runTest {
        // The scalar mapper maps the SQL NULL row to a null element; singleOrNull() must return it
        // as null (non-null underlying type).
        // given
        database.execute("INSERT INTO converter (text) VALUES (NULL)")

        // when & then
        val result: UserName? = kueryClient.sql {
            +"SELECT text FROM converter"
        }.singleOrNull()
        assertThat(result).isNull()
    }

    @Test
    fun `singleOrNull maps a nullable-underlying value class scalar SQL NULL to an inner null`() = runTest {
        // given
        database.execute("INSERT INTO converter (text) VALUES (NULL)")

        // when & then
        val result: OptionalUserName? = kueryClient.sql {
            +"SELECT text FROM converter"
        }.singleOrNull()
        assertThat(result).isEqualTo(OptionalUserName(null))
    }

    @Test
    fun `singleOrNull returns a value class scalar when a row matches`() = runTest {
        // given
        database.execute("INSERT INTO converter (text) VALUES ('user1')")

        // when & then
        val result: UserName? = kueryClient.sql {
            +"SELECT text FROM converter"
        }.singleOrNull()
        assertThat(result).isEqualTo(UserName("user1"))
    }

    @Test
    fun `singleOrNull returns null for a nullable-underlying value class scalar when no row matches`() = runTest {
        // No row at all maps to the outer null, distinct from a SQL NULL row of a nullable-underlying
        // value class, which maps to OptionalUserName(null).
        // when & then
        val result: OptionalUserName? = kueryClient.sql {
            +"SELECT text FROM converter WHERE text = 'absent'"
        }.singleOrNull()
        assertThat(result).isNull()
    }

    @Test
    fun `single rejects a SQL NULL value class scalar with a non-null underlying`() = runTest {
        // The underlying type is non-null, so a SQL NULL cannot be held in the value class; single()
        // must fail rather than hand back a null under a non-null return type. (singleOrNull()
        // tolerates it and returns null; see the test above.)
        // given
        database.execute("INSERT INTO converter (text) VALUES (NULL)")

        // when & then
        assertFailure {
            kueryClient.sql {
                +"SELECT text FROM converter"
            }.single<UserName>()
        }.isInstanceOf(TypeMismatchDataAccessException::class)
    }

    @Test
    fun `non-nullable value class property rejects SQL NULL`() = runTest {
        // The property and its underlying type are both non-null, so a SQL NULL cannot be taken into
        // the value class; construction fails rather than yielding a null value class.
        // given
        database.execute("INSERT INTO converter (text) VALUES (NULL)")

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
    fun `value class with a secondary constructor is boxed through its primary constructor`() = runTest {
        // A secondary constructor leaves no single constructor-impl/box-impl pair, so boxing falls
        // back to the kotlin-reflect primary constructor rather than the fast path.
        // given
        database.execute("INSERT INTO converter (text) VALUES ('user1')")

        // when & then
        val result: Token = kueryClient.sql {
            +"SELECT text FROM converter"
        }.single()
        assertThat(result).isEqualTo(Token("user1"))
    }

    @Test
    fun `init validation surfaces as the original exception for a value class with a secondary constructor`() =
        runTest {
            // The kotlin-reflect constructor path wraps init failures in InvocationTargetException; it
            // must be unwrapped so the require() IllegalArgumentException surfaces directly.
            // given
            database.execute("INSERT INTO converter (text) VALUES ('')")

            // when & then
            assertFailure {
                kueryClient.sql {
                    +"SELECT text FROM converter"
                }.list<Token>()
            }.isInstanceOf(IllegalArgumentException::class)
                .hasMessage("must not be empty")
        }

    @Test
    fun `generic value class is rejected as a scalar with a clear error`() = runTest {
        // Same fail-fast as the data class property position, but with the generic value class as the
        // fetch type itself: its underlying is a type parameter, so it cannot be boxed automatically.
        // given
        database.execute("INSERT INTO converter (text) VALUES ('user1')")

        // when & then
        assertFailure {
            kueryClient.sql {
                +"SELECT text FROM converter"
            }.list<Wrapped<String>>()
        }.isInstanceOf(IllegalArgumentException::class)
            .messageContains("generic value class")
    }

    @Test
    fun `generic value class scalar is mapped through a registered reading converter`() = runTest {
        // A generic value class cannot be boxed automatically, but a registered converter serves it
        // as the fetch type itself (it wins before the boxing fail-fast).
        // given
        val kueryClient = database.kueryClient(listOf(StringToWrappedConverter()))
        database.execute("INSERT INTO converter (text) VALUES ('user1')")

        // when & then
        val result: Wrapped<String> = kueryClient.sql {
            +"SELECT text FROM converter"
        }.single()
        assertThat(result).isEqualTo(Wrapped("user1"))
    }

    @Test
    fun `generic value class scalar keeps SQL NULL as a null element`() = runTest {
        // A generic value class has no known underlying type, so it cannot take a SQL NULL as an
        // inner null; the SQL NULL row is kept as a null element, like a non-null-underlying scalar.
        // given
        val kueryClient = database.kueryClient(listOf(StringToWrappedConverter()))
        database.execute("INSERT INTO converter (text) VALUES ('user1'), (NULL)")

        // when & then
        val result: List<Wrapped<String>?> = kueryClient.sql {
            +"SELECT text FROM converter ORDER BY id"
        }.list<Wrapped<String>>()
        assertThat(result).isEqualTo(listOf(Wrapped("user1"), null))
    }

    @Test
    fun `nullable-underlying value class property maps SQL NULL to an inner null`() = runTest {
        // The property is non-null but its underlying type is nullable, so SQL NULL is taken into
        // the value class as OptionalUserName(null) rather than failing.
        // given
        database.execute("INSERT INTO converter (text) VALUES (NULL)")

        // when & then
        val record: OptRecord = kueryClient.sql {
            +"SELECT text FROM converter"
        }.single()
        assertThat(record.text).isEqualTo(OptionalUserName(null))
    }

    @Test
    fun `doubly nullable value class property maps SQL NULL to the outer null`() = runTest {
        // Both the property and the underlying type are nullable, so SQL NULL cannot be resolved
        // unambiguously; it maps to the outer null (property is null), not OptionalUserName(null).
        // given
        database.execute("INSERT INTO converter (text) VALUES (NULL)")

        // when & then
        val record: OptNullableRecord = kueryClient.sql {
            +"SELECT text FROM converter"
        }.single()
        assertThat(record.text).isNull()
    }

    // NOTE: `fetching a value class scalar from a multi-column result fails` is jdbc-only (see the
    // jdbc module's ValueClassFetchTest): the r2dbc scalar path does not validate the column count.

    @Test
    fun `value class as a data class property is mapped from the underlying column`() = runTest {
        // given
        database.execute("INSERT INTO converter (text) VALUES ('user1')")

        // when & then
        val record: Record = kueryClient.sql {
            +"SELECT id, text FROM converter"
        }.single()
        assertThat(record.text).isEqualTo(UserName("user1"))
    }

    @Test
    fun `nullable value class property maps SQL NULL to null`() = runTest {
        // given
        database.execute("INSERT INTO converter (text) VALUES (NULL)")

        // when & then
        val record: NullableRecord = kueryClient.sql {
            +"SELECT id, text FROM converter"
        }.single()
        assertThat(record.text).isNull()
    }

    @Test
    fun `value class property wrapping an enum is mapped`() = runTest {
        // given
        database.execute("INSERT INTO converter (text) VALUES ('HOGE')")

        // when & then
        val record: EnumRecord = kueryClient.sql {
            +"SELECT text FROM converter"
        }.single()
        assertThat(record.text).isEqualTo(Status(SampleEnum.HOGE))
    }

    @Test
    fun `value class wrapping an enum is mapped as a scalar`() = runTest {
        // The scalar path (value class as the fetch type itself) recurses value class -> enum: the
        // column String is coerced to the enum underlying through the ConversionService, then boxed.
        // given
        database.execute("INSERT INTO converter (text) VALUES ('HOGE')")

        // when & then
        val result: List<Status> = kueryClient.sql {
            +"SELECT text FROM converter"
        }.list()
        assertThat(result).isEqualTo(listOf(Status(SampleEnum.HOGE)))
    }

    @Test
    fun `each value class constructor parameter is mapped from its own column`() = runTest {
        // A data class with more than one value class parameter: each parameter must be resolved
        // from its own matched column independently, with no cross-talk between converters.
        // given
        database.execute("INSERT INTO converter (text) VALUES ('user1')")

        // when & then
        val record: MultiValueClassRecord = kueryClient.sql {
            +"SELECT text AS name, id AS amount FROM converter"
        }.single()
        assertThat(record.name).isEqualTo(UserName("user1"))
        assertThat(record.amount).isEqualTo(Amount(1))
    }

    @Test
    fun `default value applies when the column for a defaulted value class parameter is SQL NULL`() = runTest {
        // A value class parameter with a Kotlin default: a SQL NULL yields a null converted value,
        // which is omitted for the optional parameter so the default value class applies (mirrors
        // the non-value-class defaulted parameter path).
        // given
        database.execute("INSERT INTO converter (text) VALUES (NULL)")

        // when & then
        val record: DefaultedValueClassRecord = kueryClient.sql {
            +"SELECT text FROM converter"
        }.single()
        assertThat(record.text).isEqualTo(UserName("default"))
    }

    @Test
    fun `init validation runs when mapping a value class`() = runTest {
        // given
        database.execute("INSERT INTO converter (text) VALUES ('')")

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
        val kueryClient = database.kueryClient(listOf(StringToUserNameConverter()))
        database.execute("INSERT INTO converter (text) VALUES ('user1')")

        // when & then
        val record: Record = kueryClient.sql {
            +"SELECT id, text FROM converter"
        }.single()
        assertThat(record.text).isEqualTo(UserName("custom:user1"))
    }

    @Test
    fun `snake_case column maps to a camelCase value class property`() = runTest {
        // given
        database.execute("INSERT INTO converter (text) VALUES ('user1')")

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
        database.execute("INSERT INTO converter (text) VALUES ('user1')")

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
        database.execute("INSERT INTO converter (text) VALUES ('user1')")

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
        database.execute("INSERT INTO converter (text) VALUES ('user1')")

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
        database.execute("INSERT INTO converter (text) VALUES ('user1')")

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
        database.execute("INSERT INTO converter (text) VALUES ('user1')")

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
        database.execute("INSERT INTO converter (text) VALUES ('user1')")

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
        val kueryClient = database.kueryClient(listOf(StringToWrappedConverter()))
        database.execute("INSERT INTO converter (text) VALUES ('user1')")

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
        val kueryClient = database.kueryClient(listOf(LongToStringIdConverter()))
        database.execute("INSERT INTO converter (text) VALUES ('x')")

        // when & then
        val result: StringId = kueryClient.sql {
            +"SELECT id FROM converter"
        }.single()
        assertThat(result).isEqualTo(StringId("custom:1"))
    }

    // NOTE: two further driver-coercion cases are jdbc-only (see the jdbc module's
    // ValueClassFetchTest): r2dbc-h2's `readable.get(index, type)` does not coerce a numeric
    // column to a different type (it returns the raw value or throws), so the driver never
    // produces a differently-typed value there:
    //   - `driver coercion remains available when no reading converter matches`
    //     (BIGINT -> Boolean via ResultSet.getBoolean).
    //   - `a reading converter from the driver typed value wins over automatic boxing`
    //     (BIGINT retrieved as the String underlying, then a Converter<String, StringId> applied).

    @Test
    fun `value class underlying type is coerced from the raw column type when no converter matches`() = runTest {
        // The BIGINT id is retrieved raw as Long and coerced to the Int underlying via the
        // ConversionService (no converter targets Amount).
        // given
        database.execute("INSERT INTO converter (text) VALUES ('x')")

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
        val kueryClient = database.kueryClient(listOf(StringToMoneyConverter()))
        database.execute("INSERT INTO converter (text) VALUES ('\$10.00')")

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
        val kueryClient = database.kueryClient(listOf(BlankToNullBigDecimalConverter()))
        database.execute("INSERT INTO converter (text) VALUES ('')")

        // when & then
        val record: NullableMoneyRecord = kueryClient.sql {
            +"SELECT text FROM converter"
        }.single()
        assertThat(record.text).isNull()
    }

    @Test
    fun `a missing column for a constructor parameter throws even if the property is nullable`() = runTest {
        // Same exception as the plain data class path (DataClassColumnMismatchContract); the
        // concrete type is module-specific, see ExceptionProfile.columnMismatchException.
        // given
        database.execute("INSERT INTO converter (text) VALUES ('user1')")

        // when & then
        assertFailure {
            kueryClient.sql {
                +"SELECT text FROM converter"
            }.single(MismatchRecord::class)
        }.isInstanceOf(exceptionProfile.columnMismatchException)
    }

    @Test
    fun `value class mutable body property is populated when no constructor parameter is a value class`() = runTest {
        // The constructor takes no value class parameter, so mapping stays on Spring's
        // DataClassRowMapper; a value class mutable body property is still populated through the
        // inherited setter + ConversionService path.
        // given
        database.execute("INSERT INTO converter (text) VALUES ('user1')")

        // when & then
        val record: BodyValueClassRecord = kueryClient.sql {
            +"SELECT id, text AS name FROM converter"
        }.single()
        assertThat(record.name).isEqualTo(UserName("user1"))
    }

    @Test
    fun `value class mutable body property is populated alongside a value class constructor parameter`() = runTest {
        // A value class constructor parameter routes to ValueClassPropertyRowMapper; a value class
        // mutable body property is still populated through the inherited setter path.
        // given
        database.execute("INSERT INTO converter (text) VALUES ('user1')")

        // when & then
        val record: ConstructorAndBodyValueClassRecord = kueryClient.sql {
            +"SELECT text, text AS name FROM converter"
        }.single()
        assertThat(record.text).isEqualTo(UserName("user1"))
        assertThat(record.name).isEqualTo(UserName("user1"))
    }

    data class Record(
        val id: Long,
        val text: UserName,
    )

    data class NullableRecord(
        val id: Long,
        val text: UserName?,
    )

    data class BodyValueClassRecord(val id: Long) {
        var name: UserName? = null
    }

    data class ConstructorAndBodyValueClassRecord(val text: UserName) {
        var name: UserName? = null
    }

    data class NameRecord(val text: UserName)

    data class MultiValueClassRecord(
        val name: UserName,
        val amount: Amount,
    )

    data class DefaultedValueClassRecord(val text: UserName = UserName("default"))

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
}
