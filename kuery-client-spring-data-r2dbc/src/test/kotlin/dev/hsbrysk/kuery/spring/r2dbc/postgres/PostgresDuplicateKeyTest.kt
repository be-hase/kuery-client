package dev.hsbrysk.kuery.spring.r2dbc.postgres

import dev.hsbrysk.kuery.spring.testing.contract.postgres.PostgresDuplicateKeyContract

class PostgresDuplicateKeyTest : PostgresDuplicateKeyContract() {
    override val database get() = postgres

    companion object {
        private val postgres = R2dbcPostgresContractDatabase()
    }
}
