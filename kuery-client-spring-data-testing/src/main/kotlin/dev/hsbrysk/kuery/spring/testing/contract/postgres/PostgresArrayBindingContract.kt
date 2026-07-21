package dev.hsbrysk.kuery.spring.testing.contract.postgres

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.spring.testing.ContractDatabase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.WritingConverter

/**
 * Contract shared by the jdbc and r2dbc modules. Each module runs it via a concrete subclass
 * that supplies its `ContractDatabase`. Behavior that legitimately diverges (char[] handling and
 * value classes wrapping primitive arrays) stays on the concrete subclasses.
 */
@Suppress("TooManyFunctions")
abstract class PostgresArrayBindingContract {
    protected abstract val database: ContractDatabase

    // lazy: `database` is not resolvable yet while this base class initializes.
    protected val kueryClient: KueryClient by lazy { database.kueryClient() }

    /**
     * Reads a native array column value back as an object array. The jdbc driver surfaces the
     * column as a `java.sql.Array` that must be unwrapped, while r2dbc hands back the object
     * array directly.
     */
    protected abstract fun readNativeArray(value: Any?): Array<*>

    enum class SampleEnum {
        HOGE,
        FUGA,
    }

    data class StringWrapper(val value: String)

    @JvmInline
    value class UserName(val value: String)

    @JvmInline
    value class Wrapped<T>(val value: T)

    @JvmInline
    value class Status(val value: SampleEnum)

    @JvmInline
    value class OptionalStatus(val value: SampleEnum?)

    @WritingConverter
    class StringWrapperToStringConverter : Converter<StringWrapper, String> {
        override fun convert(source: StringWrapper): String = source.value
    }

    @BeforeEach
    fun setUpUsersTable() {
        database.execute(
            """
            CREATE TABLE users (
                user_id SERIAL PRIMARY KEY,
                username VARCHAR(50) NOT NULL,
                tags TEXT[],
                scores INT[]
            )
            """.trimIndent(),
        )
        database.execute("INSERT INTO users (username) VALUES ('HOGE'), ('FUGA'), ('PIYO')")
    }

    @AfterEach
    fun dropUsersTable() {
        database.execute("DROP TABLE IF EXISTS users")
    }

    @Test
    fun `bind array to ANY predicate`() = runTest {
        // given
        val usernames = arrayOf("HOGE", "FUGA")

        // when
        val list = kueryClient
            .sql { +"SELECT username FROM users WHERE username = ANY($usernames) ORDER BY user_id" }
            .listMap()

        // then
        assertThat(list.map { it["username"] }).isEqualTo(listOf("HOGE", "FUGA"))
    }

    @Test
    fun `bind enum array to ANY predicate`() = runTest {
        // given
        val usernames = arrayOf(SampleEnum.HOGE, SampleEnum.FUGA)

        // when
        val list = kueryClient
            .sql { +"SELECT username FROM users WHERE username = ANY($usernames) ORDER BY user_id" }
            .listMap()

        // then
        assertThat(list.map { it["username"] }).isEqualTo(listOf("HOGE", "FUGA"))
    }

    @Test
    fun `bind value class array to ANY predicate`() = runTest {
        // given
        val usernames = arrayOf(UserName("HOGE"), UserName("FUGA"))

        // when
        val list = kueryClient
            .sql { +"SELECT username FROM users WHERE username = ANY($usernames) ORDER BY user_id" }
            .listMap()

        // then
        assertThat(list.map { it["username"] }).isEqualTo(listOf("HOGE", "FUGA"))
    }

    @Test
    fun `bind value class wrapping enum array to ANY predicate`() = runTest {
        // The value class recurses value class -> enum -> String, so the array binds as String[]
        // with the enum names.
        // given
        val statuses = arrayOf(Status(SampleEnum.HOGE), Status(SampleEnum.FUGA))

        // when
        val list = kueryClient
            .sql { +"SELECT username FROM users WHERE username = ANY($statuses) ORDER BY user_id" }
            .listMap()

        // then
        assertThat(list.map { it["username"] }).isEqualTo(listOf("HOGE", "FUGA"))
    }

    @Test
    fun `bind empty value class wrapping enum array to a native array column`() = runTest {
        // The component type carries the value class -> enum -> String intent even with no elements,
        // so an empty array still binds as String[].
        // given
        val tags = arrayOf<Status>()

        // when
        val count = kueryClient
            .sql { +"INSERT INTO users (username, tags) VALUES ('tagged', $tags)" }
            .rowsUpdated()

        // then
        assertThat(count).isEqualTo(1L)

        val record = kueryClient
            .sql { +"SELECT tags FROM users WHERE username = 'tagged'" }
            .singleMap()
        val stored = readNativeArray(record["tags"])
        assertThat(stored.toList()).isEqualTo(emptyList<String>())
    }

    @Test
    fun `bind all-inner-null value class wrapping enum array to a native array column`() = runTest {
        // Every element is an inner null (nullable underlying enum); the component type is still
        // resolved to String[] from the value class, so the array binds with null elements.
        // given
        val tags = arrayOf(OptionalStatus(null), OptionalStatus(null))

        // when
        val count = kueryClient
            .sql { +"INSERT INTO users (username, tags) VALUES ('tagged', $tags)" }
            .rowsUpdated()

        // then
        assertThat(count).isEqualTo(1L)

        val record = kueryClient
            .sql { +"SELECT tags FROM users WHERE username = 'tagged'" }
            .singleMap()
        val stored = readNativeArray(record["tags"])
        assertThat(stored.toList()).isEqualTo(listOf(null, null))
    }

    @Test
    fun `bind generic value class array infers the element type`() = runTest {
        // A generic value class's underlying erases to Object; the concrete component type
        // (String[]) must be inferred from the unwrapped elements, not left as Object[] which
        // the driver rejects.
        // given
        val usernames = arrayOf(Wrapped("HOGE"), Wrapped("FUGA"))

        // when
        val list = kueryClient
            .sql { +"SELECT username FROM users WHERE username = ANY($usernames) ORDER BY user_id" }
            .listMap()

        // then
        assertThat(list.map { it["username"] }).isEqualTo(listOf("HOGE", "FUGA"))
    }

    @Test
    fun `bind generic value class array of a non-string type infers the element type`() = runTest {
        // The inferred component type follows the actual unwrapped elements (Integer[] here),
        // not a hardcoded String[].
        // given
        val ids = arrayOf(Wrapped(1), Wrapped(2))

        // when
        val list = kueryClient
            .sql { +"SELECT username FROM users WHERE user_id = ANY($ids) ORDER BY user_id" }
            .listMap()

        // then
        assertThat(list.map { it["username"] }).isEqualTo(listOf("HOGE", "FUGA"))
    }

    @Test
    fun `bind all-null value class array to a native array column`() = runTest {
        // given
        val tags = arrayOf<UserName?>(null, null)

        // when
        val count = kueryClient
            .sql { +"INSERT INTO users (username, tags) VALUES ('tagged', $tags)" }
            .rowsUpdated()

        // then
        assertThat(count).isEqualTo(1L)

        val record = kueryClient
            .sql { +"SELECT tags FROM users WHERE username = 'tagged'" }
            .singleMap()
        val stored = readNativeArray(record["tags"])
        assertThat(stored.toList()).isEqualTo(listOf(null, null))
    }

    @Test
    fun `bind custom-converted array to ANY predicate`() = runTest {
        // given
        val kueryClient = database.kueryClient(listOf(StringWrapperToStringConverter()))
        val usernames = arrayOf(StringWrapper("HOGE"), StringWrapper("FUGA"))

        // when
        val list = kueryClient
            .sql { +"SELECT username FROM users WHERE username = ANY($usernames) ORDER BY user_id" }
            .listMap()

        // then
        assertThat(list.map { it["username"] }).isEqualTo(listOf("HOGE", "FUGA"))
    }

    @Test
    fun `bind array to a native array column`() = runTest {
        // given
        val tags = arrayOf("a", "b")

        // when
        val count = kueryClient
            .sql { +"INSERT INTO users (username, tags) VALUES ('tagged', $tags)" }
            .rowsUpdated()

        // then
        assertThat(count).isEqualTo(1L)

        val record = kueryClient
            .sql { +"SELECT tags FROM users WHERE username = 'tagged'" }
            .singleMap()
        val stored = readNativeArray(record["tags"])
        assertThat(stored.toList()).isEqualTo(listOf("a", "b"))
    }

    @Test
    fun `bind all-null enum array to a native array column`() = runTest {
        // given
        val tags = arrayOf<SampleEnum?>(null, null)

        // when
        val count = kueryClient
            .sql { +"INSERT INTO users (username, tags) VALUES ('tagged', $tags)" }
            .rowsUpdated()

        // then
        assertThat(count).isEqualTo(1L)

        val record = kueryClient
            .sql { +"SELECT tags FROM users WHERE username = 'tagged'" }
            .singleMap()
        val stored = readNativeArray(record["tags"])
        assertThat(stored.toList()).isEqualTo(listOf(null, null))
    }

    @Test
    fun `bind empty enum array to a native array column`() = runTest {
        // given
        val tags = arrayOf<SampleEnum>()

        // when
        val count = kueryClient
            .sql { +"INSERT INTO users (username, tags) VALUES ('tagged', $tags)" }
            .rowsUpdated()

        // then
        assertThat(count).isEqualTo(1L)

        val record = kueryClient
            .sql { +"SELECT tags FROM users WHERE username = 'tagged'" }
            .singleMap()
        val stored = readNativeArray(record["tags"])
        assertThat(stored.toList()).isEqualTo(emptyList<String>())
    }

    @Test
    fun `bind primitive int array to ANY predicate`() = runTest {
        // given
        val userIds = intArrayOf(1, 2)

        // when
        val list = kueryClient
            .sql { +"SELECT username FROM users WHERE user_id = ANY($userIds) ORDER BY user_id" }
            .listMap()

        // then
        assertThat(list.map { it["username"] }).isEqualTo(listOf("HOGE", "FUGA"))
    }

    @Test
    fun `bind primitive long array to ANY predicate`() = runTest {
        // given
        val userIds = longArrayOf(1, 2)

        // when
        val list = kueryClient
            .sql { +"SELECT username FROM users WHERE user_id = ANY($userIds) ORDER BY user_id" }
            .listMap()

        // then
        assertThat(list.map { it["username"] }).isEqualTo(listOf("HOGE", "FUGA"))
    }

    @Test
    fun `bind primitive short array to ANY predicate`() = runTest {
        // given
        val userIds = shortArrayOf(1, 2)

        // when
        val list = kueryClient
            .sql { +"SELECT username FROM users WHERE user_id = ANY($userIds) ORDER BY user_id" }
            .listMap()

        // then
        assertThat(list.map { it["username"] }).isEqualTo(listOf("HOGE", "FUGA"))
    }

    @Test
    fun `bind primitive double array to ANY predicate`() = runTest {
        // given
        val userIds = doubleArrayOf(1.0, 2.0)

        // when
        val list = kueryClient
            .sql { +"SELECT username FROM users WHERE user_id = ANY($userIds) ORDER BY user_id" }
            .listMap()

        // then
        assertThat(list.map { it["username"] }).isEqualTo(listOf("HOGE", "FUGA"))
    }

    @Test
    fun `bind primitive float array to ANY predicate`() = runTest {
        // given
        val userIds = floatArrayOf(1.0f, 2.0f)

        // when
        val list = kueryClient
            .sql { +"SELECT username FROM users WHERE user_id = ANY($userIds) ORDER BY user_id" }
            .listMap()

        // then
        assertThat(list.map { it["username"] }).isEqualTo(listOf("HOGE", "FUGA"))
    }

    @Test
    fun `bind primitive boolean array round-trips through select`() = runTest {
        // given
        val flags = booleanArrayOf(true, false)

        // when
        val record = kueryClient
            .sql { +"SELECT $flags AS flags" }
            .singleMap()

        // then
        assertThat(readNativeArray(record["flags"]).toList()).isEqualTo(listOf(true, false))
    }
}
