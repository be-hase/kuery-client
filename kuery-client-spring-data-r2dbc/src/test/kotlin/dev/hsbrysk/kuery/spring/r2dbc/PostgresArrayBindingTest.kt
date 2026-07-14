package dev.hsbrysk.kuery.spring.r2dbc

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.awaitRowsUpdated

class PostgresArrayBindingTest {
    private val kueryClient = postgres.kueryClient()

    enum class SampleEnum {
        HOGE,
        FUGA,
    }

    @BeforeEach
    fun setUp() = runTest {
        postgres.databaseClient.sql(
            """
            CREATE TABLE users (
                user_id SERIAL PRIMARY KEY,
                username VARCHAR(50) NOT NULL,
                tags TEXT[]
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
        val usernames = arrayOf("HOGE", "FUGA")
        val list = kueryClient
            .sql { +"SELECT username FROM users WHERE username = ANY($usernames) ORDER BY user_id" }
            .listMap()
        assertThat(list.map { it["username"] }).isEqualTo(listOf("HOGE", "FUGA"))
    }

    @Test
    fun `bind enum array to ANY predicate`() = runTest {
        val usernames = arrayOf(SampleEnum.HOGE, SampleEnum.FUGA)
        val list = kueryClient
            .sql { +"SELECT username FROM users WHERE username = ANY($usernames) ORDER BY user_id" }
            .listMap()
        assertThat(list.map { it["username"] }).isEqualTo(listOf("HOGE", "FUGA"))
    }

    @Test
    fun `bind array to a native array column`() = runTest {
        val tags = arrayOf("a", "b")
        val count = kueryClient
            .sql { +"INSERT INTO users (username, tags) VALUES ('tagged', $tags)" }
            .rowsUpdated()
        assertThat(count).isEqualTo(1L)

        val record = kueryClient
            .sql { +"SELECT tags FROM users WHERE username = 'tagged'" }
            .singleMap()
        val stored = record["tags"] as Array<*>
        assertThat(stored.toList()).isEqualTo(listOf("a", "b"))
    }

    companion object {
        private val postgres = PostgresTestContainer()

        @JvmStatic
        @AfterAll
        fun afterAll() {
            postgres.close()
        }
    }
}
