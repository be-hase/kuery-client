package dev.hsbrysk.kuery.spring.jdbc.conversion

import dev.hsbrysk.kuery.spring.jdbc.JdbcH2ContractDatabase
import dev.hsbrysk.kuery.spring.testing.contract.conversion.ValueClassConversionContract

class ValueClassConversionTest : ValueClassConversionContract() {
    override val database get() = h2

    companion object {
        private val h2 = JdbcH2ContractDatabase()
    }
}
