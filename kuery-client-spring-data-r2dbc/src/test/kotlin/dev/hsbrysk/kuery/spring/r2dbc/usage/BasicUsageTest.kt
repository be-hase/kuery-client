package dev.hsbrysk.kuery.spring.r2dbc.usage

import dev.hsbrysk.kuery.spring.r2dbc.R2dbcH2ContractDatabase
import dev.hsbrysk.kuery.spring.r2dbc.r2dbcExceptionProfile
import dev.hsbrysk.kuery.spring.testing.contract.usage.BasicUsageContract

class BasicUsageTest : BasicUsageContract() {
    override val database get() = h2

    override val exceptionProfile get() = r2dbcExceptionProfile

    companion object {
        private val h2 = R2dbcH2ContractDatabase()
    }
}
