package dev.hsbrysk.kuery.spring.jdbc.postgres

import dev.hsbrysk.kuery.spring.testing.contract.postgres.PostgresCastBindingContract

class PostgresCastBindingTest : PostgresCastBindingContract() {
    override val database get() = postgres

    companion object {
        private val postgres = JdbcPostgresContractDatabase()
    }
}
