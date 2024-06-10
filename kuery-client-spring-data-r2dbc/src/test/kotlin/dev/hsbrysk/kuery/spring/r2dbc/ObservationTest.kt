package dev.hsbrysk.kuery.spring.r2dbc

import dev.hsbrysk.kuery.core.list
import dev.hsbrysk.kuery.core.single
import dev.hsbrysk.kuery.core.singleOrNull
import io.micrometer.observation.tck.TestObservationRegistry
import io.micrometer.observation.tck.TestObservationRegistryAssert
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.awaitRowsUpdated

class ObservationTest {
    private val registry = TestObservationRegistry.create()
    private val kueryClient = mysql.kueryClient(observationRegistry = registry)

    @BeforeEach
    fun setUp() = runTest {
        mysql.databaseClient.sql(
            """
            CREATE TABLE users (
                user_id INT AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(50) NOT NULL,
                email VARCHAR(100) NOT NULL
            );

            INSERT INTO users (username, email) VALUES
            ('user1', 'user1@example.com'),
            ('user2', 'user2@example.com');
            """.trimIndent(),
        ).fetch().awaitRowsUpdated()
    }

    @AfterEach
    fun testDown() = runTest {
        mysql.databaseClient.sql(
            """
            DROP TABLE users;
            """.trimIndent(),
        ).fetch().awaitRowsUpdated()
    }

    data class User(val userId: Int, val username: String, val email: String)

    @Test
    fun singleMap() = runTest {
        kueryClient.sql { +"SELECT * FROM users WHERE user_id = ${bind(1)}" }.singleMap()
        assertObservation(
            sqlId = "dev.hsbrysk.kuery.spring.r2dbc.ObservationTest.singleMap",
            sql = "SELECT * FROM users WHERE user_id = :p0",
        )
    }

    @Test
    fun singleMapOrNull() = runTest {
        kueryClient.sql { +"SELECT * FROM users WHERE user_id = ${bind(1)}" }.singleMapOrNull()
        assertObservation(
            sqlId = "dev.hsbrysk.kuery.spring.r2dbc.ObservationTest.singleMapOrNull",
            sql = "SELECT * FROM users WHERE user_id = :p0",
        )
    }

    @Test
    fun single() = runTest {
        kueryClient.sql { +"SELECT * FROM users WHERE user_id = ${bind(1)}" }.single<User>()
        assertObservation(
            sqlId = "dev.hsbrysk.kuery.spring.r2dbc.ObservationTest.single",
            sql = "SELECT * FROM users WHERE user_id = :p0",
        )
    }

    @Test
    fun singleOrNull() = runTest {
        kueryClient.sql { +"SELECT * FROM users WHERE user_id = ${bind(1)}" }.singleOrNull<User>()
        assertObservation(
            sqlId = "dev.hsbrysk.kuery.spring.r2dbc.ObservationTest.singleOrNull",
            sql = "SELECT * FROM users WHERE user_id = :p0",
        )
    }

    @Test
    fun listMap() = runTest {
        kueryClient.sql { +"SELECT * FROM users" }.listMap()
        assertObservation(
            sqlId = "dev.hsbrysk.kuery.spring.r2dbc.ObservationTest.listMap",
            sql = "SELECT * FROM users",
        )
    }

    @Test
    fun list() = runTest {
        kueryClient.sql { +"SELECT * FROM users" }.list<User>()
        assertObservation(
            sqlId = "dev.hsbrysk.kuery.spring.r2dbc.ObservationTest.list",
            sql = "SELECT * FROM users",
        )
    }

    @Test
    fun rowUpdated() = runTest {
        kueryClient
            .sql {
                +"INSERT INTO users (username, email) VALUES (${bind("user3")}, ${bind("user3@example.com")})"
            }
            .rowsUpdated()
        assertObservation(
            sqlId = "dev.hsbrysk.kuery.spring.r2dbc.ObservationTest.rowUpdated",
            sql = "INSERT INTO users (username, email) VALUES (:p0, :p1)",
        )
    }

    @Test
    fun generatedValues() = runTest {
        kueryClient
            .sql {
                +"INSERT INTO users (username, email) VALUES (${bind("user3")}, ${bind("user3@example.com")})"
            }
            .generatedValues("user_id")
        assertObservation(
            sqlId = "dev.hsbrysk.kuery.spring.r2dbc.ObservationTest.generatedValues",
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
