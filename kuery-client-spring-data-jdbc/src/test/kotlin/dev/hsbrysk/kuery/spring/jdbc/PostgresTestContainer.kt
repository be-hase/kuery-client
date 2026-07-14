package dev.hsbrysk.kuery.spring.jdbc

import dev.hsbrysk.kuery.core.KueryBlockingClient
import io.micrometer.observation.ObservationRegistry
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.jdbc.core.simple.JdbcClient
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
    val dataSource = PGSimpleDataSource().apply {
        setURL(postgresContainer.jdbcUrl)
        user = postgresContainer.username
        password = postgresContainer.password
    }
    val jdbcClient: JdbcClient = JdbcClient.create(dataSource)

    fun kueryClient(
        converters: List<Any> = emptyList(),
        observationRegistry: ObservationRegistry? = null,
    ): KueryBlockingClient = SpringJdbcKueryClient.builder()
        .dataSource(dataSource)
        .converters(converters)
        .apply {
            observationRegistry?.let { observationRegistry(it) }
        }
        .build()

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
