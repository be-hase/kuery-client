package dev.hsbrysk.kuery.spring.r2dbc

import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.spring.testing.ContractDatabase
import io.micrometer.observation.ObservationRegistry
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.runBlocking
import org.springframework.data.r2dbc.dialect.DialectResolver
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.awaitRowsUpdated
import java.util.UUID

/**
 * In-memory H2 [ContractDatabase] backed by the r2dbc implementation, for contract tests that
 * exercise Kuery Client's own layer (interpolation, FetchSpec, converters, ...). Driver-dependent
 * behavior is tested against real databases in the `mysql` / `postgres` packages instead.
 *
 * `DATABASE_TO_UPPER=FALSE` + `CASE_INSENSITIVE_IDENTIFIERS=TRUE` makes H2 keep identifiers as
 * written while matching them case-insensitively, mirroring MySQL's behavior.
 */
class R2dbcH2ContractDatabase : ContractDatabase {
    val connectionFactory: ConnectionFactory = ConnectionFactories.get(
        "r2dbc:h2:mem:///${UUID.randomUUID()}" +
            "?options=DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
    )
    val databaseClient: DatabaseClient = DatabaseClient.builder()
        .connectionFactory(connectionFactory)
        .bindMarkers(DialectResolver.getDialect(connectionFactory).bindMarkersFactory)
        .build()

    override fun kueryClient(
        converters: List<Any>,
        observationRegistry: ObservationRegistry?,
    ): KueryClient = SpringR2dbcKueryClient.builder()
        .connectionFactory(connectionFactory)
        .converters(converters)
        .apply {
            observationRegistry?.let { observationRegistry(it) }
        }
        .build()

    override fun execute(sql: String) {
        runBlocking {
            databaseClient.sql(sql).fetch().awaitRowsUpdated()
        }
    }
}
