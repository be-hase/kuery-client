package dev.hsbrysk.kuery.spring.jdbc.mysql

import com.mysql.cj.jdbc.MysqlDataSource
import dev.hsbrysk.kuery.core.KueryBlockingClient
import dev.hsbrysk.kuery.spring.jdbc.SpringJdbcKueryClient
import io.micrometer.observation.ObservationRegistry
import org.springframework.jdbc.core.simple.JdbcClient
import org.testcontainers.mysql.MySQLContainer

/**
 * Shared across all test classes in this package. Started lazily once per test JVM; Testcontainers'
 * Ryuk container removes it after the JVM exits.
 */
object MySqlTestContainer {
    private val mysqlContainer = MySQLContainer("mysql:8.0.37").also { it.start() }
    val dataSource = MysqlDataSource().apply {
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
}
