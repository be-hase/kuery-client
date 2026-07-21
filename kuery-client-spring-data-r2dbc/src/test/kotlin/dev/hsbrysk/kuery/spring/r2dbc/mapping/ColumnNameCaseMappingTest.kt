package dev.hsbrysk.kuery.spring.r2dbc.mapping

import dev.hsbrysk.kuery.spring.r2dbc.R2dbcH2ContractDatabase
import dev.hsbrysk.kuery.spring.testing.contract.mapping.ColumnNameCaseMappingContract

class ColumnNameCaseMappingTest : ColumnNameCaseMappingContract() {
    override val database get() = h2

    companion object {
        private val h2 = R2dbcH2ContractDatabase()
    }
}
