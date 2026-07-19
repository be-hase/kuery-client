package dev.hsbrysk.kuery.spring.r2dbc.postgres

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.WritingConverter
import org.springframework.r2dbc.core.awaitRowsUpdated

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
    value class Scores(val value: IntArray?)

    @WritingConverter
    class StringWrapperToStringConverter : Converter<StringWrapper, String> {
        override fun convert(source: StringWrapper): String = source.value
    }

    @BeforeEach
    fun setUp() = runTest {
        postgres.databaseClient.sql(
            """
            CREATE TABLE users (
                user_id SERIAL PRIMARY KEY,
                username VARCHAR(50) NOT NULL,
                tags TEXT[],
                scores INT[]
            )
            """.trimIndent(),
        ).fetch().awaitRowsUpdated()
        postgres.databaseClient.sql("INSERT INTO users (username) VALUES ('HOGE'), ('FUGA'), ('PIYO')")
            .fetch().awaitRowsUpdated()
    }

    @AfterEach
    fun tearDown() = runTest {
        postgres.databaseClient.sql("DROP TABLE users").fetch().awaitRowsUpdated()
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
        val stored = record["tags"] as Array<*>
        assertThat(stored.toList()).isEqualTo(listOf(null, null))
    }

    @Test
    fun `bind custom-converted array to ANY predicate`() = runTest {
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
        val stored = record["tags"] as Array<*>
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
        val stored = record["tags"] as Array<*>
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
        val stored = record["tags"] as Array<*>
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
        assertThat((record["flags"] as Array<*>).toList()).isEqualTo(listOf(true, false))
    }

    @Test
    fun `bind primitive char array round-trips through select`() = runTest {
        // The r2dbc module boxes char arrays to String[]. The jdbc module passes char[] through to
        // pgjdbc, which has no SQL array mapping for it, so its counterpart pins a rejection.
        // given
        val chars = charArrayOf('a', 'b')

        // when
        val record = kueryClient
            .sql { +"SELECT $chars AS chars" }
            .singleMap()

        // then
        assertThat((record["chars"] as Array<*>).toList()).isEqualTo(listOf("a", "b"))
    }

    // R2DBC-only: only the r2dbc client boxes primitive arrays (the jdbc client passes them to the
    // driver as-is and binds an untyped null), so there is no jdbc counterpart to these two tests.
    @Test
    fun `value class wrapping a primitive array binds the boxed array`() = runTest {
        // given
        val scores = Scores(intArrayOf(1, 2))

        // when
        kueryClient
            .sql { +"INSERT INTO users (username, scores) VALUES ('scored', $scores)" }
            .rowsUpdated()

        // then
        val record = kueryClient
            .sql { +"SELECT scores FROM users WHERE username = 'scored'" }
            .singleMap()
        assertThat((record["scores"] as Array<*>).toList()).isEqualTo(listOf(1, 2))
    }

    @Test
    fun `value class wrapping a null primitive array binds SQL NULL`() = runTest {
        // The bindNull type must be the boxed array type the driver would have received for a
        // non-null value, not the raw primitive array type, which r2dbc has no codec for.
        // given
        val scores = Scores(null)

        // when
        kueryClient
            .sql { +"INSERT INTO users (username, scores) VALUES ('scored', $scores)" }
            .rowsUpdated()

        // then
        val record = kueryClient
            .sql { +"SELECT scores FROM users WHERE username = 'scored'" }
            .singleMap()
        assertThat(record["scores"]).isEqualTo(null)
    }

    companion object {
        private val postgres = PostgresTestContainer
    }
}
