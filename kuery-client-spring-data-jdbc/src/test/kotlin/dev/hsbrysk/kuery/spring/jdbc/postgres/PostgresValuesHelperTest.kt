package dev.hsbrysk.kuery.spring.jdbc.postgres

import dev.hsbrysk.kuery.spring.jdbc.jdbcExceptionProfile
import dev.hsbrysk.kuery.spring.testing.contract.postgres.PostgresValuesHelperContract

class PostgresValuesHelperTest : PostgresValuesHelperContract() {
    override val database get() = postgres

    override val exceptionProfile get() = jdbcExceptionProfile

    companion object {
        private val postgres = JdbcPostgresContractDatabase()
    }
}
