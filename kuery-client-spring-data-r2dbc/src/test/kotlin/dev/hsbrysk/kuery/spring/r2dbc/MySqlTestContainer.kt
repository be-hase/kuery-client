package dev.hsbrysk.kuery.spring.r2dbc

import dev.hsbrysk.kuery.core.KueryClient
import io.micrometer.observation.ObservationRegistry
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
import org.springframework.data.r2dbc.dialect.DialectResolver
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.awaitRowsUpdated
import org.testcontainers.containers.MySQLContainer

class MySqlTestContainer : AutoCloseable {
    private val mysqlContainer = MySQLContainer("mysql:8.0.37").also { it.start() }
    private val connectionFactory = connectionFactory()
    val databaseClient = DatabaseClient.builder()
        .connectionFactory(connectionFactory)
        .bindMarkers(DialectResolver.getDialect(connectionFactory).bindMarkersFactory)
        .build()

    fun kueryClient(
        converters: List<Any> = emptyList(),
        observationRegistry: ObservationRegistry? = null,
    ): KueryClient {
        return SpringR2dbcKueryClient.builder()
            .connectionFactory(connectionFactory)
            .converters(converters)
            .apply {
                observationRegistry?.let { observationRegistry(it) }
            }
            .build()
    }

    suspend fun setUpForConverterTest() {
        databaseClient.sql(
            """
            CREATE TABLE `converter`
            (
                `id` BIGINT AUTO_INCREMENT,
                `text` VARCHAR(255) DEFAULT NULL,
                PRIMARY KEY (`id`)
            ) ENGINE = InnoDB
              DEFAULT CHARSET = utf8mb4
              COLLATE = utf8mb4_bin;
            """.trimIndent(),
        ).fetch().awaitRowsUpdated()
    }

    suspend fun tearDownForConverterTest() {
        databaseClient.sql("DROP TABLE converter").fetch().awaitRowsUpdated()
    }

    private fun connectionFactory(): ConnectionFactory {
        val url = mysqlContainer.jdbcUrl.replace("jdbc", "r2dbc")
        val options = ConnectionFactoryOptions.parse(url).mutate()
            .option(ConnectionFactoryOptions.USER, mysqlContainer.username)
            .option(ConnectionFactoryOptions.PASSWORD, mysqlContainer.password)
            .build()
        return ConnectionFactories.get(options)
    }

    override fun close() {
        mysqlContainer.close()
    }
}
