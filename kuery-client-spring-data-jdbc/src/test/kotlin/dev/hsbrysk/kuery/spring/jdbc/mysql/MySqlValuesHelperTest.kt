package dev.hsbrysk.kuery.spring.jdbc.mysql

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.values
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * Contrast to PostgresValuesHelperTest: MySQL does not infer VALUES parameter types per row, so
 * mixing null and non-null rows on a typed column just works.
 */
class MySqlValuesHelperTest {
    private val kueryClient = mysql.kueryClient()

    @BeforeEach
    fun setUp() {
        mysql.jdbcClient.sql(
            """
            CREATE TABLE documents (
                id INT PRIMARY KEY,
                parent_id BINARY(16),
                created_at DATETIME
            )
            """.trimIndent(),
        ).update()
    }

    @AfterEach
    fun tearDown() {
        mysql.jdbcClient.sql("DROP TABLE documents").update()
    }

    @Test
    fun `values mixing null and non-null rows on typed columns`() {
        val createdAt = LocalDateTime.of(2026, 1, 1, 0, 0, 0)
        val input = listOf(
            listOf(1, ByteArray(16) { it.toByte() }, createdAt),
            listOf(2, null, null),
        )

        val rowsUpdated = kueryClient.sql {
            +"INSERT INTO documents (id, parent_id, created_at)"
            values(input)
        }.rowsUpdated()
        assertThat(rowsUpdated).isEqualTo(2L)
    }

    companion object {
        private val mysql = MySqlTestContainer
    }
}
