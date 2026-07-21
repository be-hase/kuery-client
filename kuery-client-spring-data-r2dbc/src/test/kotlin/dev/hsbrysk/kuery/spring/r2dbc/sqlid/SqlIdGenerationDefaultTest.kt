package dev.hsbrysk.kuery.spring.r2dbc.sqlid

import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.spring.r2dbc.R2dbcH2ContractDatabase
import dev.hsbrysk.kuery.spring.r2dbc.SpringR2dbcKueryClient
import dev.hsbrysk.kuery.spring.testing.contract.sqlid.SqlIdGenerationDefaultContract
import io.micrometer.observation.ObservationRegistry
import java.util.concurrent.CopyOnWriteArrayList

class SqlIdGenerationDefaultTest : SqlIdGenerationDefaultContract() {
    override val database get() = h2

    override val capturedSqlIds = CopyOnWriteArrayList<String?>()

    override fun capturingKueryClient(
        observationRegistry: ObservationRegistry?,
        enableAutoSqlIdGeneration: Boolean?,
    ): KueryClient = SpringR2dbcKueryClient.builder()
        .connectionFactory(SqlIdCapturingConnectionFactory(h2.connectionFactory, capturedSqlIds))
        .apply { observationRegistry?.let { observationRegistry(it) } }
        .apply { enableAutoSqlIdGeneration?.let { enableAutoSqlIdGeneration(it) } }
        .build()

    companion object {
        private val h2 = R2dbcH2ContractDatabase()
    }
}
