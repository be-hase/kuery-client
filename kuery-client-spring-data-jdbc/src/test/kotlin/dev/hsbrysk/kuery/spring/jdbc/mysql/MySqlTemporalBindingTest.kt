package dev.hsbrysk.kuery.spring.jdbc.mysql

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.single
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class MySqlTemporalBindingTest {
    private val kueryClient = mysql.kueryClient()

    @BeforeEach
    fun setUp() {
        mysql.jdbcClient.sql(
            """
            CREATE TABLE events (
                id INT AUTO_INCREMENT PRIMARY KEY,
                d DATE,
                dt DATETIME(6),
                t TIME(6),
                ts TIMESTAMP(6)
            )
            """.trimIndent(),
        ).update()
    }

    @AfterEach
    fun tearDown() {
        mysql.jdbcClient.sql("DROP TABLE events").update()
    }

    @Test
    fun `LocalDate round-trips through a DATE column`() {
        val d = LocalDate.of(2026, 7, 17)
        kueryClient.sql { +"INSERT INTO events (d) VALUES ($d)" }.rowsUpdated()

        val stored: LocalDate = kueryClient.sql { +"SELECT d FROM events WHERE d = $d" }.single()
        assertThat(stored).isEqualTo(d)
    }

    @Test
    fun `LocalDateTime with microseconds round-trips through a DATETIME column`() {
        val dt = LocalDateTime.of(2026, 7, 17, 12, 34, 56, 789_012_000)
        kueryClient.sql { +"INSERT INTO events (dt) VALUES ($dt)" }.rowsUpdated()

        val stored: LocalDateTime = kueryClient.sql { +"SELECT dt FROM events WHERE dt = $dt" }.single()
        assertThat(stored).isEqualTo(dt)
    }

    @Test
    fun `LocalTime round-trips through a TIME column`() {
        val t = LocalTime.of(12, 34, 56)
        kueryClient.sql { +"INSERT INTO events (t) VALUES ($t)" }.rowsUpdated()

        val stored: LocalTime = kueryClient.sql { +"SELECT t FROM events WHERE t = $t" }.single()
        assertThat(stored).isEqualTo(t)
    }

    @Test
    fun `Instant round-trips through a TIMESTAMP column`() {
        val ts = Instant.parse("2026-07-17T03:04:05.678901Z")
        kueryClient.sql { +"INSERT INTO events (ts) VALUES ($ts)" }.rowsUpdated()

        val stored: Instant = kueryClient.sql { +"SELECT ts FROM events WHERE ts = $ts" }.single()
        assertThat(stored).isEqualTo(ts)
    }

    companion object {
        private val mysql = MySqlTestContainer
    }
}
