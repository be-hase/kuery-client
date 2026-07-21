package dev.hsbrysk.kuery.spring.r2dbc.postgres

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.spring.testing.contract.postgres.PostgresArrayBindingContract
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class PostgresArrayBindingTest : PostgresArrayBindingContract() {
    override val database get() = postgres

    @JvmInline
    value class Scores(val value: IntArray?)

    override fun readNativeArray(value: Any?): Array<*> = value as Array<*>

    @Test
    fun `bind primitive char array round-trips through select`() = runTest {
        // The r2dbc module boxes char arrays to String[]. The jdbc module passes char[] through to
        // pgjdbc, which has no SQL array mapping for it, so its counterpart pins a rejection.
        // given
        val chars = charArrayOf('a', 'b')

        // when
        val record = kueryClient
            .sql { +"SELECT $chars AS chars" }
            .singleMap()

        // then
        assertThat((record["chars"] as Array<*>).toList()).isEqualTo(listOf("a", "b"))
    }

    // R2DBC-only: only the r2dbc client boxes primitive arrays (the jdbc client passes them to the
    // driver as-is and binds an untyped null), so there is no jdbc counterpart to these two tests.
    @Test
    fun `value class wrapping a primitive array binds the boxed array`() = runTest {
        // given
        val scores = Scores(intArrayOf(1, 2))

        // when
        kueryClient
            .sql { +"INSERT INTO users (username, scores) VALUES ('scored', $scores)" }
            .rowsUpdated()

        // then
        val record = kueryClient
            .sql { +"SELECT scores FROM users WHERE username = 'scored'" }
            .singleMap()
        assertThat((record["scores"] as Array<*>).toList()).isEqualTo(listOf(1, 2))
    }

    @Test
    fun `value class wrapping a null primitive array binds SQL NULL`() = runTest {
        // The bindNull type must be the boxed array type the driver would have received for a
        // non-null value, not the raw primitive array type, which r2dbc has no codec for.
        // given
        val scores = Scores(null)

        // when
        kueryClient
            .sql { +"INSERT INTO users (username, scores) VALUES ('scored', $scores)" }
            .rowsUpdated()

        // then
        val record = kueryClient
            .sql { +"SELECT scores FROM users WHERE username = 'scored'" }
            .singleMap()
        assertThat(record["scores"]).isEqualTo(null)
    }

    companion object {
        private val postgres = R2dbcPostgresContractDatabase()
    }
}
