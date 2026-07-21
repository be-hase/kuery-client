package dev.hsbrysk.kuery.spring.jdbc.postgres

import dev.hsbrysk.kuery.spring.testing.contract.postgres.PostgresDuplicateKeyContract

class PostgresDuplicateKeyTest : PostgresDuplicateKeyContract() {
    override val database get() = postgres

    companion object {
        private val postgres = JdbcPostgresContractDatabase()
    }
}
