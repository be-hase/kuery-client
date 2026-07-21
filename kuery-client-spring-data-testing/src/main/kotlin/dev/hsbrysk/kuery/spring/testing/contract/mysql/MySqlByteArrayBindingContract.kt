package dev.hsbrysk.kuery.spring.testing.contract.mysql

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.core.single
import dev.hsbrysk.kuery.core.singleOrNull
import dev.hsbrysk.kuery.spring.testing.ContractDatabase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Contract shared by the jdbc and r2dbc modules. Each module runs it via a concrete subclass
 * that supplies its `ContractDatabase`.
 *
 * A ByteArray must be bound as a single binary value (e.g. `BINARY(16)` keys), never expanded
 * like a collection.
 */
abstract class MySqlByteArrayBindingContract {
    protected abstract val database: ContractDatabase

    // lazy: `database` is not resolvable yet while this base class initializes.
    protected val kueryClient: KueryClient by lazy { database.kueryClient() }

    @BeforeEach
    fun setUpBinariesTable() {
        database.execute(
            """
            CREATE TABLE binaries (
                id BINARY(16) PRIMARY KEY,
                data VARBINARY(255) NOT NULL
            )
            """.trimIndent(),
        )
    }

    @AfterEach
    fun dropBinariesTable() {
        database.execute("DROP TABLE IF EXISTS binaries")
    }

    @Test
    fun `bind ByteArray as a single binary value`() = runTest {
        // given
        val id = ByteArray(16) { it.toByte() }
        val data = byteArrayOf(1, 2, 3)

        // when
        val inserted = kueryClient
            .sql { +"INSERT INTO binaries (id, data) VALUES ($id, $data)" }
            .rowsUpdated()

        // then
        assertThat(inserted).isEqualTo(1L)

        val stored = kueryClient
            .sql { +"SELECT data FROM binaries WHERE id = $id" }
            .single<ByteArray>()
        assertThat(stored.toList()).isEqualTo(data.toList())
    }

    @Test
    fun `bind the same ByteArray twice in one statement`() = runTest {
        // given
        val id = ByteArray(16) { it.toByte() }
        val data = byteArrayOf(1, 2, 3)
        kueryClient
            .sql { +"INSERT INTO binaries (id, data) VALUES ($id, $data)" }
            .rowsUpdated()

        // when
        val count = kueryClient
            .sql { +"SELECT COUNT(*) FROM binaries WHERE id = $id AND data = $data AND data = $data" }
            .single<Long>()

        // then
        assertThat(count).isEqualTo(1L)
    }

    @Test
    fun `singleOrNull ByteArray returns null when no row matches`() = runTest {
        // given
        val id = ByteArray(16) { it.toByte() }

        // when
        val stored = kueryClient
            .sql { +"SELECT data FROM binaries WHERE id = $id" }
            .singleOrNull<ByteArray>()

        // then
        assertThat(stored).isNull()
    }
}
