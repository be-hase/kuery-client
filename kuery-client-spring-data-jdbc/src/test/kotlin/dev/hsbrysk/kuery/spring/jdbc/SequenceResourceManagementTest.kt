package dev.hsbrysk.kuery.spring.jdbc

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEmpty
import assertk.assertions.isTrue
import dev.hsbrysk.kuery.core.sequence
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.concurrent.CopyOnWriteArrayList
import javax.sql.DataSource

class SequenceResourceManagementTest {
    private val kueryClient = SpringJdbcKueryClient.builder()
        .dataSource(trackingDataSource)
        .build()

    @BeforeEach
    fun beforeEach() {
        mysql.jdbcClient.sql(
            """
            CREATE TABLE users (
                user_id INT AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(50) NOT NULL
            )
            """.trimIndent(),
        ).update()
        mysql.jdbcClient.sql("INSERT INTO users (username) VALUES ('user1'), ('user2')").update()
        trackingDataSource.connections.clear()
    }

    @AfterEach
    fun afterEach() {
        mysql.jdbcClient.sql("DROP TABLE users").update()
    }

    @Test
    fun `sequence releases the connection after full iteration`() {
        val result: List<User> = kueryClient.sql { +"SELECT * FROM users" }.sequence<User>().toList()
        assertThat(result).isEqualTo(
            listOf(
                User(userId = 1, username = "user1"),
                User(userId = 2, username = "user2"),
            ),
        )
        assertAllTrackedConnectionsClosed()
    }

    @Test
    fun `sequenceMap releases the connection after full iteration`() {
        val result = kueryClient.sql { +"SELECT * FROM users" }.sequenceMap().toList()
        assertThat(result).isEqualTo(
            listOf(
                mapOf("user_id" to 1, "username" to "user1"),
                mapOf("user_id" to 2, "username" to "user2"),
            ),
        )
        assertAllTrackedConnectionsClosed()
    }

    @Test
    fun `sequence releases the connection when row mapping fails`() {
        assertFailure {
            kueryClient.sql { +"SELECT username FROM users" }.sequence<Int>().toList()
        }
        assertAllTrackedConnectionsClosed()
    }

    @Test
    fun `sequence releases the connection when closed via use after partial iteration`() {
        val result = kueryClient.sql { +"SELECT * FROM users" }.sequence<User>().use { seq ->
            seq.first()
        }
        assertThat(result).isEqualTo(User(userId = 1, username = "user1"))
        assertAllTrackedConnectionsClosed()
    }

    @Test
    fun `sequence can be iterated only once`() {
        val seq = kueryClient.sql { +"SELECT * FROM users" }.sequence<User>()
        seq.toList()
        assertFailure { seq.toList() }.isInstanceOf(IllegalStateException::class)
    }

    private fun assertAllTrackedConnectionsClosed() {
        assertThat(trackingDataSource.connections).isNotEmpty()
        trackingDataSource.connections.forEach {
            assertThat(it.isClosed).isTrue()
        }
    }

    data class User(
        val userId: Int,
        val username: String,
    )

    private class TrackingDataSource(private val delegate: DataSource) : DataSource by delegate {
        val connections = CopyOnWriteArrayList<Connection>()

        override fun getConnection(): Connection = delegate.connection.also { connections.add(it) }
    }

    companion object {
        private val mysql = MySqlTestContainer()
        private val trackingDataSource = TrackingDataSource(mysql.dataSource)

        @AfterAll
        @JvmStatic
        fun afterAll() {
            mysql.close()
        }
    }
}
