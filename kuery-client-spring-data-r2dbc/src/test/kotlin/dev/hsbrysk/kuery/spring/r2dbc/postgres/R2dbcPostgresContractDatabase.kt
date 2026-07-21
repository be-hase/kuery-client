package dev.hsbrysk.kuery.spring.r2dbc.postgres

import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.spring.testing.ContractDatabase
import io.micrometer.observation.ObservationRegistry
import kotlinx.coroutines.runBlocking
import org.springframework.r2dbc.core.awaitRowsUpdated

/**
 * PostgreSQL [ContractDatabase] backed by the r2dbc implementation, delegating to the
 * [PostgresTestContainer] singleton shared with this package's non-contract tests.
 */
class R2dbcPostgresContractDatabase : ContractDatabase {
    override fun kueryClient(
        converters: List<Any>,
        observationRegistry: ObservationRegistry?,
    ): KueryClient = PostgresTestContainer.kueryClient(converters, observationRegistry)

    override fun execute(sql: String) {
        runBlocking {
            PostgresTestContainer.databaseClient.sql(sql).fetch().awaitRowsUpdated()
        }
    }
}
