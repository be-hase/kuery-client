package dev.hsbrysk.kuery.spring.jdbc.mapping

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.spring.jdbc.JdbcH2ContractDatabase
import dev.hsbrysk.kuery.spring.testing.contract.mapping.DuplicateColumnLabelContract
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Note the jdbc/r2dbc divergence: spring-jdbc's ColumnMapRowMapper keeps the FIRST occurrence of a
 * duplicated label, while spring-r2dbc's column map keeps the LAST. Alias duplicated columns
 * explicitly when joining tables that share column names.
 */
class DuplicateColumnLabelTest : DuplicateColumnLabelContract() {
    override val database get() = h2

    @Test
    fun `singleMap keeps the first value when a join produces duplicate column labels`() = runTest {
        val record = singleMapWithDuplicateLabels()

        assertThat(record["name"]).isEqualTo("alice")
    }

    companion object {
        private val h2 = JdbcH2ContractDatabase()
    }
}
