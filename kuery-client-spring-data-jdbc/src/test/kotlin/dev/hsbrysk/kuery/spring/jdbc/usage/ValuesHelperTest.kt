package dev.hsbrysk.kuery.spring.jdbc.usage

import dev.hsbrysk.kuery.spring.jdbc.JdbcH2ContractDatabase
import dev.hsbrysk.kuery.spring.testing.contract.usage.ValuesHelperContract

class ValuesHelperTest : ValuesHelperContract() {
    override val database get() = h2

    companion object {
        private val h2 = JdbcH2ContractDatabase()
    }
}
