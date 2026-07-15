package dev.hsbrysk.kuery.spring.jdbc.postgres

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.WritingConverter
import java.sql.Array as SqlArray

class ArrayBindingTest {
    private val kueryClient = postgres.kueryClient()

    enum class SampleEnum {
        HOGE,
        FUGA,
    }

    data class StringWrapper(val value: String)

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
        val usernames = arrayOf("HOGE", "FUGA")
        val list = kueryClient
            .sql { +"SELECT username FROM users WHERE username = ANY($usernames) ORDER BY user_id" }
            .listMap()
        assertThat(list.map { it["username"] }).isEqualTo(listOf("HOGE", "FUGA"))
    }

    @Test
    fun `bind enum array to ANY predicate`() {
        val usernames = arrayOf(SampleEnum.HOGE, SampleEnum.FUGA)
        val list = kueryClient
            .sql { +"SELECT username FROM users WHERE username = ANY($usernames) ORDER BY user_id" }
            .listMap()
        assertThat(list.map { it["username"] }).isEqualTo(listOf("HOGE", "FUGA"))
    }

    @Test
    fun `bind custom-converted array to ANY predicate`() {
        val kueryClient = postgres.kueryClient(listOf(StringWrapperToStringConverter()))
        val usernames = arrayOf(StringWrapper("HOGE"), StringWrapper("FUGA"))
        val list = kueryClient
            .sql { +"SELECT username FROM users WHERE username = ANY($usernames) ORDER BY user_id" }
            .listMap()
        assertThat(list.map { it["username"] }).isEqualTo(listOf("HOGE", "FUGA"))
    }

    @Test
    fun `bind array to a native array column`() {
        val tags = arrayOf("a", "b")
        val count = kueryClient
            .sql { +"INSERT INTO users (username, tags) VALUES ('tagged', $tags)" }
            .rowsUpdated()
        assertThat(count).isEqualTo(1L)

        val record = kueryClient
            .sql { +"SELECT tags FROM users WHERE username = 'tagged'" }
            .singleMap()
        val stored = (record["tags"] as SqlArray).array as Array<*>
        assertThat(stored.toList()).isEqualTo(listOf("a", "b"))
    }

    @Test
    fun `bind all-null enum array to a native array column`() {
        val tags = arrayOf<SampleEnum?>(null, null)
        val count = kueryClient
            .sql { +"INSERT INTO users (username, tags) VALUES ('tagged', $tags)" }
            .rowsUpdated()
        assertThat(count).isEqualTo(1L)

        val record = kueryClient
            .sql { +"SELECT tags FROM users WHERE username = 'tagged'" }
            .singleMap()
        val stored = (record["tags"] as SqlArray).array as Array<*>
        assertThat(stored.toList()).isEqualTo(listOf(null, null))
    }

    @Test
    fun `bind empty enum array to a native array column`() {
        val tags = arrayOf<SampleEnum>()
        val count = kueryClient
            .sql { +"INSERT INTO users (username, tags) VALUES ('tagged', $tags)" }
            .rowsUpdated()
        assertThat(count).isEqualTo(1L)

        val record = kueryClient
            .sql { +"SELECT tags FROM users WHERE username = 'tagged'" }
            .singleMap()
        val stored = (record["tags"] as SqlArray).array as Array<*>
        assertThat(stored.toList()).isEqualTo(emptyList<String>())
    }

    @Test
    fun `bind primitive int array to ANY predicate`() {
        val userIds = intArrayOf(1, 2)
        val list = kueryClient
            .sql { +"SELECT username FROM users WHERE user_id = ANY($userIds) ORDER BY user_id" }
            .listMap()
        assertThat(list.map { it["username"] }).isEqualTo(listOf("HOGE", "FUGA"))
    }

    companion object {
        private val postgres = PostgresTestContainer
    }
}
