package dev.hsbrysk.kuery.spring.r2dbc

import com.example.spring.r2dbc.UserRepository
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
    private val userRepository = UserRepository(kueryClient)

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

    @Test
    fun singleMap() = runTest {
        userRepository.singleMap(1)
        assertObservation(
            sqlId = "com.example.spring.r2dbc.UserRepository.singleMap",
            sql = "SELECT * FROM users WHERE user_id = :p0",
        )
    }

    @Test
    fun singleMapOrNull() = runTest {
        userRepository.singleMapOrNull(1)
        assertObservation(
            sqlId = "com.example.spring.r2dbc.UserRepository.singleMapOrNull",
            sql = "SELECT * FROM users WHERE user_id = :p0",
        )
    }

    @Test
    fun single() = runTest {
        userRepository.single(1)
        assertObservation(
            sqlId = "com.example.spring.r2dbc.UserRepository.single",
            sql = "SELECT * FROM users WHERE user_id = :p0",
        )
    }

    @Test
    fun singleOrNull() = runTest {
        userRepository.singleOrNull(1)
        assertObservation(
            sqlId = "com.example.spring.r2dbc.UserRepository.singleOrNull",
            sql = "SELECT * FROM users WHERE user_id = :p0",
        )
    }

    @Test
    fun listMap() = runTest {
        userRepository.listMap()
        assertObservation(
            sqlId = "com.example.spring.r2dbc.UserRepository.listMap",
            sql = "SELECT * FROM users",
        )
    }

    @Test
    fun list() = runTest {
        userRepository.list()
        assertObservation(
            sqlId = "com.example.spring.r2dbc.UserRepository.list",
            sql = "SELECT * FROM users",
        )
    }

    @Test
    fun rowUpdated() = runTest {
        userRepository.rowUpdated("user3", "user3@example.com")
        assertObservation(
            sqlId = "com.example.spring.r2dbc.UserRepository.rowUpdated",
            sql = "INSERT INTO users (username, email) VALUES (:p0, :p1)",
        )
    }

    @Test
    fun generatedValues() = runTest {
        userRepository.generatedValues("user3", "user3@example.com")
        assertObservation(
            sqlId = "com.example.spring.r2dbc.UserRepository.generatedValues",
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
