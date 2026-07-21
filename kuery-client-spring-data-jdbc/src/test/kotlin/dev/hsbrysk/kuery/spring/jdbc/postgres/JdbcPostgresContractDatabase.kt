package dev.hsbrysk.kuery.spring.jdbc.postgres

import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.spring.testing.BlockingKueryClientAdapter
import dev.hsbrysk.kuery.spring.testing.ContractDatabase
import io.micrometer.observation.ObservationRegistry

/**
 * PostgreSQL [ContractDatabase] backed by the jdbc implementation, delegating to the
 * [PostgresTestContainer] singleton shared with this package's non-contract tests.
 */
class JdbcPostgresContractDatabase : ContractDatabase {
    override fun kueryClient(
        converters: List<Any>,
        observationRegistry: ObservationRegistry?,
    ): KueryClient = BlockingKueryClientAdapter(PostgresTestContainer.kueryClient(converters, observationRegistry))

    override fun execute(sql: String) {
        PostgresTestContainer.jdbcClient.sql(sql).update()
    }
}
