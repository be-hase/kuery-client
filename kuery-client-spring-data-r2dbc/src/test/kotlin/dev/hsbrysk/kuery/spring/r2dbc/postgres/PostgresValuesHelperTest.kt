package dev.hsbrysk.kuery.spring.r2dbc.postgres

import dev.hsbrysk.kuery.spring.r2dbc.r2dbcExceptionProfile
import dev.hsbrysk.kuery.spring.testing.contract.postgres.PostgresValuesHelperContract

class PostgresValuesHelperTest : PostgresValuesHelperContract() {
    override val database get() = postgres

    override val exceptionProfile get() = r2dbcExceptionProfile

    companion object {
        private val postgres = R2dbcPostgresContractDatabase()
    }
}
