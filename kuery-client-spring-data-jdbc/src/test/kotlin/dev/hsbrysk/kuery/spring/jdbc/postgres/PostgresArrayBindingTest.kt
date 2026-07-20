package dev.hsbrysk.kuery.spring.jdbc.postgres

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.WritingConverter
import org.springframework.jdbc.BadSqlGrammarException
import java.sql.Array as SqlArray

class PostgresArrayBindingTest {
    private val kueryClient = postgres.kueryClient()

    enum class SampleEnum {
        HOGE,
        FUGA,
    }

    data class StringWrapper(val value: String)

    @JvmInline
    value class UserName(val value: String)

    @JvmInline
    value class Wrapped<T>(val value: T)

    @WritingConverter
    class StringWrapperToStringConverter : Converter<StringWrapper, String> {
        override fun convert(source: StringWrapper): String = source.value
    }

    @BeforeEach
    fun setUp() {
        postgres.jdbcClient.sql(
            """
            CREATE TABLE users (
                user_id SERIAL PRIMARY KEY,
                username VARCHAR(50) NOT NULL,
                tags TEXT[]
            )
            """.trimIndent(),
        ).update()
        postgres.jdbcClient.sql("INSERT INTO users (username) VALUES ('HOGE'), ('FUGA'), ('PIYO')").update()
    }

    @AfterEach
    fun tearDown() {
        postgres.jdbcClient.sql("DROP TABLE users").update()
    }

    @Test
    fun `bind array to ANY predicate`() {
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
    fun `bind enum array to ANY predicate`() {
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
    fun `bind value class array to ANY predicate`() {
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
    fun `bind generic value class array infers the element type`() {
        // A generic value class's underlying erases to Object; the concrete component type
        // (String[]) must be inferred from the unwrapped elements, not left as Object[] which
        // pgjdbc rejects.
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
    fun `bind all-null value class array to a native array column`() {
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
        val stored = (record["tags"] as SqlArray).array as Array<*>
        assertThat(stored.toList()).isEqualTo(listOf(null, null))
    }

    @Test
    fun `bind custom-converted array to ANY predicate`() {
        // given
        val kueryClient = postgres.kueryClient(listOf(StringWrapperToStringConverter()))
        val usernames = arrayOf(StringWrapper("HOGE"), StringWrapper("FUGA"))

        // when
        val list = kueryClient
            .sql { +"SELECT username FROM users WHERE username = ANY($usernames) ORDER BY user_id" }
            .listMap()

        // then
        assertThat(list.map { it["username"] }).isEqualTo(listOf("HOGE", "FUGA"))
    }

    @Test
    fun `bind array to a native array column`() {
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
        val stored = (record["tags"] as SqlArray).array as Array<*>
        assertThat(stored.toList()).isEqualTo(listOf("a", "b"))
    }

    @Test
    fun `bind all-null enum array to a native array column`() {
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
        val stored = (record["tags"] as SqlArray).array as Array<*>
        assertThat(stored.toList()).isEqualTo(listOf(null, null))
    }

    @Test
    fun `bind empty enum array to a native array column`() {
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
        val stored = (record["tags"] as SqlArray).array as Array<*>
        assertThat(stored.toList()).isEqualTo(emptyList<String>())
    }

    @Test
    fun `bind primitive int array to ANY predicate`() {
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
    fun `bind primitive long array to ANY predicate`() {
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
    fun `bind primitive short array to ANY predicate`() {
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
    fun `bind primitive double array to ANY predicate`() {
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
    fun `bind primitive float array to ANY predicate`() {
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
    fun `bind primitive boolean array round-trips through select`() {
        // given
        val flags = booleanArrayOf(true, false)

        // when
        val record = kueryClient
            .sql { +"SELECT $flags AS flags" }
            .singleMap()

        // then
        assertThat(((record["flags"] as SqlArray).array as Array<*>).toList()).isEqualTo(listOf(true, false))
    }

    @Test
    fun `bind primitive char array is rejected by the driver`() {
        // pgjdbc has no SQL array mapping for char[]. The r2dbc module boxes char arrays to
        // String[] instead, so its counterpart round-trips successfully.
        // given
        val chars = charArrayOf('a', 'b')

        // when & then
        assertFailure {
            kueryClient
                .sql { +"SELECT $chars AS chars" }
                .singleMap()
        }.isInstanceOf(BadSqlGrammarException::class)
    }

    companion object {
        private val postgres = PostgresTestContainer
    }
}
