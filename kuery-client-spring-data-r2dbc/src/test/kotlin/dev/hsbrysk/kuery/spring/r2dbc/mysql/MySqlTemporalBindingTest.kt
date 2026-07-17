package dev.hsbrysk.kuery.spring.r2dbc.mysql

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.core.single
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.awaitRowsUpdated
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class MySqlTemporalBindingTest {
    private val kueryClient: KueryClient = mysql.kueryClient()

    @BeforeEach
    fun setUp() = runTest {
        mysql.databaseClient.sql(
            """
            CREATE TABLE events (
                id INT AUTO_INCREMENT PRIMARY KEY,
                d DATE,
                dt DATETIME(6),
                t TIME(6),
                ts TIMESTAMP(6)
            )
            """.trimIndent(),
        ).fetch().awaitRowsUpdated()
    }

    @AfterEach
    fun tearDown() = runTest {
        mysql.databaseClient.sql("DROP TABLE events").fetch().awaitRowsUpdated()
    }

    @Test
    fun `LocalDate round-trips through a DATE column`() = runTest {
        val d = LocalDate.of(2026, 7, 17)
        kueryClient.sql { +"INSERT INTO events (d) VALUES ($d)" }.rowsUpdated()

        val stored: LocalDate = kueryClient.sql { +"SELECT d FROM events WHERE d = $d" }.single()
        assertThat(stored).isEqualTo(d)
    }

    @Test
    fun `LocalDateTime with microseconds round-trips through a DATETIME column`() = runTest {
        val dt = LocalDateTime.of(2026, 7, 17, 12, 34, 56, 789_012_000)
        kueryClient.sql { +"INSERT INTO events (dt) VALUES ($dt)" }.rowsUpdated()

        val stored: LocalDateTime = kueryClient.sql { +"SELECT dt FROM events WHERE dt = $dt" }.single()
        assertThat(stored).isEqualTo(dt)
    }

    @Test
    fun `LocalTime round-trips through a TIME column`() = runTest {
        val t = LocalTime.of(12, 34, 56)
        kueryClient.sql { +"INSERT INTO events (t) VALUES ($t)" }.rowsUpdated()

        val stored: LocalTime = kueryClient.sql { +"SELECT t FROM events WHERE t = $t" }.single()
        assertThat(stored).isEqualTo(t)
    }

    @Test
    fun `Instant round-trips through a TIMESTAMP column`() = runTest {
        val ts = Instant.parse("2026-07-17T03:04:05.678901Z")
        kueryClient.sql { +"INSERT INTO events (ts) VALUES ($ts)" }.rowsUpdated()

        val stored: Instant = kueryClient.sql { +"SELECT ts FROM events WHERE ts = $ts" }.single()
        assertThat(stored).isEqualTo(ts)
    }

    companion object {
        private val mysql = MySqlTestContainer
    }
}
