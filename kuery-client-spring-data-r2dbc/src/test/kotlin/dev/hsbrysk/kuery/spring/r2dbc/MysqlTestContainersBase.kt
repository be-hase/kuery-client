package dev.hsbrysk.kuery.spring.r2dbc

import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.data.r2dbc.dialect.DialectResolver
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.awaitRowsUpdated
import org.testcontainers.containers.MySQLContainer

abstract class MysqlTestContainersBase {
    protected val connectionFactory = connectionFactory()
    protected val databaseClient = DatabaseClient.builder()
        .connectionFactory(connectionFactory)
        .bindMarkers(DialectResolver.getDialect(connectionFactory).bindMarkersFactory)
        .build()
    protected val kueryClient = SpringR2dbcKueryClient.builder()
        .connectionFactory(connectionFactory)
        .converters(converters())
        .build()

    protected open fun converters(): List<Any> {
        return emptyList()
    }

    @BeforeEach
    fun baseBeforeEach() = runTest {
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

    @AfterEach
    open fun baseAfterEach() = runTest {
        databaseClient.sql("DROP TABLE converter").fetch().awaitRowsUpdated()
    }

    companion object {
        private val MYSQL = MySQLContainer("mysql:8.0.37").also { it.start() }

        internal fun connectionFactory(): ConnectionFactory {
            val url = MYSQL.jdbcUrl.replace("jdbc", "r2dbc")
            val options = ConnectionFactoryOptions.parse(url).mutate()
                .option(ConnectionFactoryOptions.USER, MYSQL.username)
                .option(ConnectionFactoryOptions.PASSWORD, MYSQL.password)
                .build()
            return ConnectionFactories.get(options)
        }
    }
}
