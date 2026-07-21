package dev.hsbrysk.kuery.spring.jdbc.sqlid

import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.spring.jdbc.JdbcH2ContractDatabase
import dev.hsbrysk.kuery.spring.jdbc.SpringJdbcKueryClient
import dev.hsbrysk.kuery.spring.testing.BlockingKueryClientAdapter
import dev.hsbrysk.kuery.spring.testing.contract.sqlid.SqlIdGenerationDefaultContract
import io.micrometer.observation.ObservationRegistry

class SqlIdGenerationDefaultTest : SqlIdGenerationDefaultContract() {
    override val database get() = h2

    override val capturedSqlIds = mutableListOf<String?>()

    override fun capturingKueryClient(
        observationRegistry: ObservationRegistry?,
        enableAutoSqlIdGeneration: Boolean?,
    ): KueryClient {
        val blockingClient = SpringJdbcKueryClient.builder()
            .dataSource(SqlIdCapturingDataSource(h2.dataSource, capturedSqlIds))
            .apply { observationRegistry?.let { observationRegistry(it) } }
            .apply { enableAutoSqlIdGeneration?.let { enableAutoSqlIdGeneration(it) } }
            .build()
        return BlockingKueryClientAdapter(blockingClient)
    }

    companion object {
        private val h2 = JdbcH2ContractDatabase()
    }
}
