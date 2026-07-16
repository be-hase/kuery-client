package dev.hsbrysk.kuery.spring.r2dbc.mysql

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.values
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.awaitRowsUpdated
import java.time.LocalDateTime

/**
 * Contrast to PostgresValuesHelperTest: MySQL does not infer VALUES parameter types per row, so
 * mixing null and non-null rows on a typed column just works.
 */
class MySqlValuesHelperTest {
    private val kueryClient = mysql.kueryClient()

    @BeforeEach
    fun setUp() = runTest {
        mysql.databaseClient.sql(
            """
            CREATE TABLE documents (
                id INT PRIMARY KEY,
                parent_id BINARY(16),
                created_at DATETIME
            )
            """.trimIndent(),
        ).fetch().awaitRowsUpdated()
    }

    @AfterEach
    fun tearDown() = runTest {
        mysql.databaseClient.sql("DROP TABLE documents").fetch().awaitRowsUpdated()
    }

    @Test
    fun `values mixing null and non-null rows on typed columns`() = runTest {
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
