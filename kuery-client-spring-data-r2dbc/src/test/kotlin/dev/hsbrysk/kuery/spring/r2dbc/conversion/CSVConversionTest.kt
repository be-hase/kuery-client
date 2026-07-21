package dev.hsbrysk.kuery.spring.r2dbc.conversion

import dev.hsbrysk.kuery.spring.r2dbc.R2dbcH2ContractDatabase
import dev.hsbrysk.kuery.spring.testing.contract.conversion.CSVConversionContract

class CSVConversionTest : CSVConversionContract() {
    override val database get() = h2

    companion object {
        private val h2 = R2dbcH2ContractDatabase()
    }
}
