package dev.hsbrysk.kuery.spring.r2dbc.mysql

import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.spring.testing.ContractDatabase
import io.micrometer.observation.ObservationRegistry
import kotlinx.coroutines.runBlocking
import org.springframework.r2dbc.core.awaitRowsUpdated

/**
 * MySQL [ContractDatabase] backed by the r2dbc implementation, delegating to the
 * [MySqlTestContainer] singleton shared with this package's non-contract tests.
 */
class R2dbcMySqlContractDatabase : ContractDatabase {
    override fun kueryClient(
        converters: List<Any>,
        observationRegistry: ObservationRegistry?,
    ): KueryClient = MySqlTestContainer.kueryClient(converters, observationRegistry)

    override fun execute(sql: String) {
        runBlocking {
            MySqlTestContainer.databaseClient.sql(sql).fetch().awaitRowsUpdated()
        }
    }
}
