package dev.hsbrysk.kuery.spring.jdbc

import dev.hsbrysk.kuery.core.list
import dev.hsbrysk.kuery.core.single
import dev.hsbrysk.kuery.core.singleOrNull
import io.micrometer.observation.tck.TestObservationRegistry
import io.micrometer.observation.tck.TestObservationRegistryAssert
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ObservationTest {
    private val registry = TestObservationRegistry.create()
    private val kueryClient = mysql.kueryClient(observationRegistry = registry)

    @BeforeEach
    fun setUp() {
        mysql.jdbcClient.sql(
            """
            CREATE TABLE users (
                user_id INT AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(50) NOT NULL,
                email VARCHAR(100) NOT NULL
            )
            """.trimIndent(),
        ).update()
        mysql.jdbcClient.sql(
            """
            INSERT INTO users (username, email) VALUES
            ('user1', 'user1@example.com'),
            ('user2', 'user2@example.com')
            """.trimIndent(),
        ).update()
    }

    @AfterEach
    fun testDown() {
        mysql.jdbcClient.sql(
            """
            DROP TABLE users;
            """.trimIndent(),
        ).update()
    }

    data class User(val userId: Int, val username: String, val email: String)

    @Test
    fun singleMap() {
        kueryClient.sql { +"SELECT * FROM users WHERE user_id = ${bind(1)}" }.singleMap()
        assertObservation(
            sqlId = "dev.hsbrysk.kuery.spring.jdbc.ObservationTest.singleMap",
            sql = "SELECT * FROM users WHERE user_id = :p0",
        )
    }

    @Test
    fun singleMapOrNull() {
        kueryClient.sql { +"SELECT * FROM users WHERE user_id = ${bind(1)}" }.singleMapOrNull()
        assertObservation(
            sqlId = "dev.hsbrysk.kuery.spring.jdbc.ObservationTest.singleMapOrNull",
            sql = "SELECT * FROM users WHERE user_id = :p0",
        )
    }

    @Test
    fun single() {
        kueryClient.sql { +"SELECT * FROM users WHERE user_id = ${bind(1)}" }.single<User>()
        assertObservation(
            sqlId = "dev.hsbrysk.kuery.spring.jdbc.ObservationTest.single",
            sql = "SELECT * FROM users WHERE user_id = :p0",
        )
    }

    @Test
    fun singleOrNull() {
        kueryClient.sql { +"SELECT * FROM users WHERE user_id = ${bind(1)}" }.singleOrNull<User>()
        assertObservation(
            sqlId = "dev.hsbrysk.kuery.spring.jdbc.ObservationTest.singleOrNull",
            sql = "SELECT * FROM users WHERE user_id = :p0",
        )
    }

    @Test
    fun listMap() {
        kueryClient.sql { +"SELECT * FROM users" }.listMap()
        assertObservation(
            sqlId = "dev.hsbrysk.kuery.spring.jdbc.ObservationTest.listMap",
            sql = "SELECT * FROM users",
        )
    }

    @Test
    fun list() {
        kueryClient.sql { +"SELECT * FROM users" }.list<User>()
        assertObservation(
            sqlId = "dev.hsbrysk.kuery.spring.jdbc.ObservationTest.list",
            sql = "SELECT * FROM users",
        )
    }

    @Test
    fun rowUpdated() {
        kueryClient
            .sql {
                +"INSERT INTO users (username, email) VALUES (${bind("user3")}, ${bind("user3@example.com")})"
            }
            .rowsUpdated()
        assertObservation(
            sqlId = "dev.hsbrysk.kuery.spring.jdbc.ObservationTest.rowUpdated",
            sql = "INSERT INTO users (username, email) VALUES (:p0, :p1)",
        )
    }

    @Test
    fun generatedValues() {
        kueryClient
            .sql {
                +"INSERT INTO users (username, email) VALUES (${bind("user3")}, ${bind("user3@example.com")})"
            }
            .generatedValues("user_id")
        assertObservation(
            sqlId = "dev.hsbrysk.kuery.spring.jdbc.ObservationTest.generatedValues",
            sql = "INSERT INTO users (username, email) VALUES (:p0, :p1)",
        )
    }

    private fun assertObservation(
        sqlId: String,
        sql: String,
    ) {
        TestObservationRegistryAssert.assertThat(registry)
            .doesNotHaveAnyRemainingCurrentObservation()
            .hasObservationWithNameEqualTo("kuery.client.fetches")
            .that()
            .hasLowCardinalityKeyValue("sql.id", sqlId)
            .hasHighCardinalityKeyValue("sql", sql)
            .hasBeenStarted()
            .hasBeenStopped()
    }

    companion object {
        private val mysql = MySqlTestContainer()

        @AfterAll
        @JvmStatic
        fun afterAll() {
            mysql.close()
        }
    }
}
