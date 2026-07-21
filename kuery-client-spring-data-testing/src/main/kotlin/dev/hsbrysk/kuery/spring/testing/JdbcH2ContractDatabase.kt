package dev.hsbrysk.kuery.spring.testing

import dev.hsbrysk.kuery.core.KueryBlockingClient
import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.spring.jdbc.SpringJdbcKueryClient
import io.micrometer.observation.ObservationRegistry
import org.h2.jdbcx.JdbcDataSource
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID

/**
 * In-memory H2 database backed by the jdbc implementation, for contract tests that exercise
 * Kuery Client's own layer (interpolation, FetchSpec, converters, ...). Driver-dependent behavior
 * is tested against real databases in the `mysql` / `postgres` packages instead.
 *
 * `DATABASE_TO_UPPER=FALSE` + `CASE_INSENSITIVE_IDENTIFIERS=TRUE` makes H2 keep identifiers as
 * written while matching them case-insensitively, mirroring MySQL's behavior.
 */
class JdbcH2ContractDatabase : ContractDatabase {
    val dataSource = JdbcDataSource().apply {
        setURL(
            "jdbc:h2:mem:${UUID.randomUUID()};" +
                "DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        )
        user = "sa"
    }
    val jdbcClient: JdbcClient = JdbcClient.create(dataSource)

    /** The raw blocking client, for jdbc-only tests (sequence, maxRows, ...). */
    fun blockingClient(
        converters: List<Any> = emptyList(),
        observationRegistry: ObservationRegistry? = null,
    ): KueryBlockingClient = SpringJdbcKueryClient.builder()
        .dataSource(dataSource)
        .converters(converters)
        .apply {
            observationRegistry?.let { observationRegistry(it) }
        }
        .build()

    override fun kueryClient(
        converters: List<Any>,
        observationRegistry: ObservationRegistry?,
    ): KueryClient = BlockingKueryClientAdapter(blockingClient(converters, observationRegistry))

    override fun execute(sql: String) {
        jdbcClient.sql(sql).update()
    }
}
