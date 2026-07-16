package dev.hsbrysk.kuery.spring.jdbc.mysql

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import dev.hsbrysk.kuery.core.single
import dev.hsbrysk.kuery.core.singleOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * A ByteArray must be bound as a single binary value (e.g. `BINARY(16)` keys), never expanded
 * like a collection.
 */
class MySqlByteArrayBindingTest {
    private val kueryClient = mysql.kueryClient()

    @BeforeEach
    fun setUp() {
        mysql.jdbcClient.sql(
            """
            CREATE TABLE binaries (
                id BINARY(16) PRIMARY KEY,
                data VARBINARY(255) NOT NULL
            )
            """.trimIndent(),
        ).update()
    }

    @AfterEach
    fun tearDown() {
        mysql.jdbcClient.sql("DROP TABLE binaries").update()
    }

    @Test
    fun `bind ByteArray as a single binary value`() {
        val id = ByteArray(16) { it.toByte() }
        val data = byteArrayOf(1, 2, 3)

        val inserted = kueryClient
            .sql { +"INSERT INTO binaries (id, data) VALUES ($id, $data)" }
            .rowsUpdated()
        assertThat(inserted).isEqualTo(1L)

        val stored = kueryClient
            .sql { +"SELECT data FROM binaries WHERE id = $id" }
            .single<ByteArray>()
        assertThat(stored.toList()).isEqualTo(data.toList())
    }

    @Test
    fun `bind the same ByteArray twice in one statement`() {
        val id = ByteArray(16) { it.toByte() }
        val data = byteArrayOf(1, 2, 3)
        kueryClient
            .sql { +"INSERT INTO binaries (id, data) VALUES ($id, $data)" }
            .rowsUpdated()

        val count = kueryClient
            .sql { +"SELECT COUNT(*) FROM binaries WHERE id = $id AND data = $data AND data = $data" }
            .single<Long>()
        assertThat(count).isEqualTo(1L)
    }

    @Test
    fun `singleOrNull ByteArray returns null when no row matches`() {
        val id = ByteArray(16) { it.toByte() }
        val stored = kueryClient
            .sql { +"SELECT data FROM binaries WHERE id = $id" }
            .singleOrNull<ByteArray>()
        assertThat(stored).isNull()
    }

    companion object {
        private val mysql = MySqlTestContainer
    }
}
