package dev.hsbrysk.kuery.spring.r2dbc.usage

import dev.hsbrysk.kuery.spring.r2dbc.R2dbcH2ContractDatabase
import dev.hsbrysk.kuery.spring.testing.contract.usage.ValuesHelperContract

class ValuesHelperTest : ValuesHelperContract() {
    override val database get() = h2

    companion object {
        private val h2 = R2dbcH2ContractDatabase()
    }
}
