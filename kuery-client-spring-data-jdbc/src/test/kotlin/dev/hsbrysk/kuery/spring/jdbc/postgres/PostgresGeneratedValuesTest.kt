package dev.hsbrysk.kuery.spring.jdbc.postgres

import dev.hsbrysk.kuery.spring.testing.contract.postgres.PostgresGeneratedValuesContract

/**
 * Pins the PostgreSQL JDBC driver's generated-values behavior: the RETURNING row reports the
 * requested column under its own name as an Int.
 */
class PostgresGeneratedValuesTest : PostgresGeneratedValuesContract() {
    override val database get() = postgres

    override val expectedGeneratedValues get() = mapOf<String, Any>("user_id" to 1)

    companion object {
        private val postgres = JdbcPostgresContractDatabase()
    }
}
