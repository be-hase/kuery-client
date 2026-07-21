package dev.hsbrysk.kuery.spring.r2dbc.conversion

import dev.hsbrysk.kuery.spring.r2dbc.R2dbcH2ContractDatabase
import dev.hsbrysk.kuery.spring.testing.contract.conversion.StringWrapperConversionContract

class StringWrapperConversionTest : StringWrapperConversionContract() {
    override val database get() = h2

    companion object {
        private val h2 = R2dbcH2ContractDatabase()
    }
}
