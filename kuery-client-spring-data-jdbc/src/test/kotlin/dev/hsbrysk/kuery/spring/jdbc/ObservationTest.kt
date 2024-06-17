package dev.hsbrysk.kuery.spring.jdbc

import com.example.spring.jdbc.UserRepository
import io.micrometer.observation.tck.TestObservationRegistry
import io.micrometer.observation.tck.TestObservationRegistryAssert
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ObservationTest {
    private val registry = TestObservationRegistry.create()
    private val kueryClient = mysql.kueryClient(observationRegistry = registry)
    private val userRepository = UserRepository(kueryClient)

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
        userRepository.singleMap(1)
        assertObservation(
            sqlId = "com.example.spring.jdbc.UserRepository.singleMap",
            sql = "SELECT * FROM users WHERE user_id = :p0",
        )
    }

    @Test
    fun singleMapOrNull() {
        userRepository.singleMapOrNull(1)
        assertObservation(
            sqlId = "com.example.spring.jdbc.UserRepository.singleMapOrNull",
            sql = "SELECT * FROM users WHERE user_id = :p0",
        )
    }

    @Test
    fun single() {
        userRepository.single(1)
        assertObservation(
            sqlId = "com.example.spring.jdbc.UserRepository.single",
            sql = "SELECT * FROM users WHERE user_id = :p0",
        )
    }

    @Test
    fun singleOrNull() {
        userRepository.singleOrNull(1)
        assertObservation(
            sqlId = "com.example.spring.jdbc.UserRepository.singleOrNull",
            sql = "SELECT * FROM users WHERE user_id = :p0",
        )
    }

    @Test
    fun listMap() {
        userRepository.listMap()
        assertObservation(
            sqlId = "com.example.spring.jdbc.UserRepository.listMap",
            sql = "SELECT * FROM users",
        )
    }

    @Test
    fun list() {
        userRepository.list()
        assertObservation(
            sqlId = "com.example.spring.jdbc.UserRepository.list",
            sql = "SELECT * FROM users",
        )
    }

    @Test
    fun rowUpdated() {
        userRepository.rowUpdated("user3", "user3@example.com")
        assertObservation(
            sqlId = "com.example.spring.jdbc.UserRepository.rowUpdated",
            sql = "INSERT INTO users (username, email) VALUES (:p0, :p1)",
        )
    }

    @Test
    fun generatedValues() {
        userRepository.generatedValues("user3", "user3@example.com")
        assertObservation(
            sqlId = "com.example.spring.jdbc.UserRepository.generatedValues",
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
