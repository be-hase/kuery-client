package dev.hsbrysk.kuery.spring.jdbc.postgres

import assertk.assertFailure
import assertk.assertions.isInstanceOf
import dev.hsbrysk.kuery.spring.testing.contract.postgres.PostgresArrayBindingContract
import org.junit.jupiter.api.Test
import org.springframework.jdbc.BadSqlGrammarException
import java.sql.Array as SqlArray

class PostgresArrayBindingTest : PostgresArrayBindingContract() {
    override val database get() = postgres

    private val blockingClient = PostgresTestContainer.kueryClient()

    override fun readNativeArray(value: Any?): Array<*> = (value as SqlArray).array as Array<*>

    @Test
    fun `bind primitive char array is rejected by the driver`() {
        // pgjdbc has no SQL array mapping for char[]. The r2dbc module boxes char arrays to
        // String[] instead, so its counterpart round-trips successfully.
        // given
        val chars = charArrayOf('a', 'b')

        // when & then
        assertFailure {
            blockingClient
                .sql { +"SELECT $chars AS chars" }
                .singleMap()
        }.isInstanceOf(BadSqlGrammarException::class)
    }

    companion object {
        private val postgres = JdbcPostgresContractDatabase()
    }
}
