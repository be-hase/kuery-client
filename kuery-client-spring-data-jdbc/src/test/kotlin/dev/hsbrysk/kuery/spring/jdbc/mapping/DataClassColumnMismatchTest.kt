package dev.hsbrysk.kuery.spring.jdbc.mapping

import dev.hsbrysk.kuery.spring.jdbc.JdbcH2ContractDatabase
import dev.hsbrysk.kuery.spring.jdbc.jdbcExceptionProfile
import dev.hsbrysk.kuery.spring.testing.contract.mapping.DataClassColumnMismatchContract

class DataClassColumnMismatchTest : DataClassColumnMismatchContract() {
    override val database get() = h2

    override val exceptionProfile get() = jdbcExceptionProfile

    companion object {
        private val h2 = JdbcH2ContractDatabase()
    }
}
