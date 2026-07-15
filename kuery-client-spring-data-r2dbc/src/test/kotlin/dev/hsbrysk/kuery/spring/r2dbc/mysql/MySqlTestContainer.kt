package dev.hsbrysk.kuery.spring.r2dbc.mysql

import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.spring.r2dbc.SpringR2dbcKueryClient
import io.micrometer.observation.ObservationRegistry
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
import org.springframework.data.r2dbc.dialect.DialectResolver
import org.springframework.r2dbc.core.DatabaseClient
import org.testcontainers.mysql.MySQLContainer

/**
 * Shared across all test classes in this package. Started lazily once per test JVM; Testcontainers'
 * Ryuk container removes it after the JVM exits.
 */
object MySqlTestContainer {
    private val mysqlContainer = MySQLContainer("mysql:8.0.37").also { it.start() }
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
        val url = mysqlContainer.jdbcUrl.replace("jdbc", "r2dbc")
        val options = ConnectionFactoryOptions.parse(url).mutate()
            .option(ConnectionFactoryOptions.USER, mysqlContainer.username)
            .option(ConnectionFactoryOptions.PASSWORD, mysqlContainer.password)
            .build()
        return ConnectionFactories.get(options)
    }
}
