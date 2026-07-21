package dev.hsbrysk.kuery.spring.jdbc.mapping

import dev.hsbrysk.kuery.spring.jdbc.JdbcH2ContractDatabase
import dev.hsbrysk.kuery.spring.testing.contract.mapping.ColumnNameCaseMappingContract

class ColumnNameCaseMappingTest : ColumnNameCaseMappingContract() {
    override val database get() = h2

    companion object {
        private val h2 = JdbcH2ContractDatabase()
    }
}
