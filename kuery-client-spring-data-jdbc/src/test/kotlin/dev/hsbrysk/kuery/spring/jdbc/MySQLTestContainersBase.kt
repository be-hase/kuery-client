package dev.hsbrysk.kuery.spring.jdbc

import com.mysql.cj.jdbc.MysqlDataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.jdbc.core.simple.JdbcClient
import org.testcontainers.containers.MySQLContainer
import javax.sql.DataSource

abstract class MySQLTestContainersBase {
    protected val dataSource = dataSource()
    protected val jdbcClient = JdbcClient.create(dataSource)
    protected val kueryClient = SpringJdbcKueryClient.builder()
        .dataSource(dataSource)
        .converters(converters())
        .build()

    protected open fun converters(): List<Any> {
        return emptyList()
    }

    @BeforeEach
    fun baseBeforeEach() {
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

    @AfterEach
    open fun baseAfterEach() {
        jdbcClient.sql("DROP TABLE converter").update()
    }

    companion object {
        private val MYSQL = MySQLContainer("mysql:8.0.37").also { it.start() }

        internal fun dataSource(): DataSource {
            return MysqlDataSource().apply {
                setURL(MYSQL.jdbcUrl)
                user = MYSQL.username
                password = MYSQL.password
            }
        }
    }
}
