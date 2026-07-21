package dev.hsbrysk.kuery.spring.r2dbc.mapping

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.spring.r2dbc.R2dbcH2ContractDatabase
import dev.hsbrysk.kuery.spring.testing.contract.mapping.DuplicateColumnLabelContract
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Note the jdbc/r2dbc divergence: spring-r2dbc's column map keeps the LAST occurrence of a
 * duplicated label, while spring-jdbc's ColumnMapRowMapper keeps the FIRST. Alias duplicated
 * columns explicitly when joining tables that share column names.
 */
class DuplicateColumnLabelTest : DuplicateColumnLabelContract() {
    override val database get() = h2

    @Test
    fun `singleMap keeps the last value when a join produces duplicate column labels`() = runTest {
        val record = singleMapWithDuplicateLabels()

        assertThat(record["name"]).isEqualTo("acme")
    }

    companion object {
        private val h2 = R2dbcH2ContractDatabase()
    }
}
