package dev.hsbrysk.kuery.spring.jdbc.mysql

import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.spring.testing.BlockingKueryClientAdapter
import dev.hsbrysk.kuery.spring.testing.ContractDatabase
import io.micrometer.observation.ObservationRegistry

/**
 * MySQL [ContractDatabase] backed by the jdbc implementation, delegating to the
 * [MySqlTestContainer] singleton shared with this package's non-contract tests.
 */
class JdbcMySqlContractDatabase : ContractDatabase {
    override fun kueryClient(
        converters: List<Any>,
        observationRegistry: ObservationRegistry?,
    ): KueryClient = BlockingKueryClientAdapter(MySqlTestContainer.kueryClient(converters, observationRegistry))

    override fun execute(sql: String) {
        MySqlTestContainer.jdbcClient.sql(sql).update()
    }
}
