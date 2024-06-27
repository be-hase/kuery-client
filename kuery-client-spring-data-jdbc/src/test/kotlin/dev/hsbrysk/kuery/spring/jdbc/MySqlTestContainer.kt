package dev.hsbrysk.kuery.spring.jdbc

import com.mysql.cj.jdbc.MysqlDataSource
import dev.hsbrysk.kuery.core.KueryBlockingClient
import io.micrometer.observation.ObservationRegistry
import org.springframework.jdbc.core.simple.JdbcClient
import org.testcontainers.containers.MySQLContainer

class MySqlTestContainer : AutoCloseable {
    private val mysqlContainer = MySQLContainer("mysql:8.0.37").also { it.start() }
    private val dataSource = MysqlDataSource().apply {
        setURL(mysqlContainer.jdbcUrl)
        user = mysqlContainer.username
        password = mysqlContainer.password
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

    fun setUpForConverterTest() {
        jdbcClient.sql(
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
        ).update()
    }

    fun tearDownForConverterTest() {
        jdbcClient.sql("DROP TABLE converter").update()
    }

    override fun close() {
        mysqlContainer.close()
    }
}
