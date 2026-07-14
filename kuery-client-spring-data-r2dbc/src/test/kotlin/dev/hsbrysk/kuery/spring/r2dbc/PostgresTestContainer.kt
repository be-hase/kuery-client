package dev.hsbrysk.kuery.spring.r2dbc

import dev.hsbrysk.kuery.core.KueryClient
import io.micrometer.observation.ObservationRegistry
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
import org.springframework.data.r2dbc.dialect.DialectResolver
import org.springframework.r2dbc.core.DatabaseClient
import org.testcontainers.postgresql.PostgreSQLContainer
import java.io.IOException
import java.net.Socket

class PostgresTestContainer : AutoCloseable {
    private val postgresContainer = PostgreSQLContainer("postgres:16").also {
        it.start()
        // Unlike MySQLContainer, PostgreSQLContainer's wait strategy only watches container
        // logs and never touches the mapped port, so the host-side port forwarding may not be
        // established yet when start() returns (observed with Rancher Desktop).
        it.awaitMappedPortReady()
    }
    val connectionFactory = connectionFactory()
    val databaseClient: DatabaseClient = DatabaseClient.builder()
        .connectionFactory(connectionFactory)
        .bindMarkers(DialectResolver.getDialect(connectionFactory).bindMarkersFactory)
        .build()

    fun kueryClient(
        converters: List<Any> = emptyList(),
        observationRegistry: ObservationRegistry? = null,
    ): KueryClient = SpringR2dbcKueryClient.builder()
        .connectionFactory(connectionFactory)
        .converters(converters)
        .apply {
            observationRegistry?.let { observationRegistry(it) }
        }
        .build()

    private fun connectionFactory(): ConnectionFactory {
        val url = postgresContainer.jdbcUrl.replace("jdbc", "r2dbc")
        val options = ConnectionFactoryOptions.parse(url).mutate()
            .option(ConnectionFactoryOptions.USER, postgresContainer.username)
            .option(ConnectionFactoryOptions.PASSWORD, postgresContainer.password)
            .build()
        return ConnectionFactories.get(options)
    }

    override fun close() {
        postgresContainer.close()
    }

    private fun PostgreSQLContainer.awaitMappedPortReady() {
        val deadline = System.currentTimeMillis() + 30_000
        while (true) {
            try {
                Socket(host, getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)).use { return }
            } catch (e: IOException) {
                if (System.currentTimeMillis() > deadline) {
                    throw e
                }
                Thread.sleep(200)
            }
        }
    }
}
