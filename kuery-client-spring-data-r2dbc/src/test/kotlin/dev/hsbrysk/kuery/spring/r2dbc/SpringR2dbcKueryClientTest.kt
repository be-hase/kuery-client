package dev.hsbrysk.kuery.spring.r2dbc

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
open class SpringR2dbcKueryClientTest {
    private val connectionFactory = connectionFactory()
    private val target = SpringR2dbcKueryClient.builder()
        .connectionFactory(connectionFactory)
        .build()

    @BeforeEach
    fun setUp() = runTest {
        target.sql {
            +"""
            CREATE TABLE `sample`
            (
                `id`              bigint AUTO_INCREMENT,
                `sample_boolean`  BOOLEAN      DEFAULT NULL,
                `sample_tinyint`  TINYINT      DEFAULT NULL,
                `sample_smallint` SMALLINT     DEFAULT NULL,
                `sample_int`      INT          DEFAULT NULL,
                `sample_bigint`   BIGINT       DEFAULT NULL,
                `sample_float`    FLOAT        DEFAULT NULL,
                `sample_double`   DOUBLE       DEFAULT NULL,
                `sample_char`     CHAR(5)      DEFAULT NULL,
                `sample_varchar`  VARCHAR(255) DEFAULT NULL,
                `sample_text`     TEXT         DEFAULT NULL,
                PRIMARY KEY (`id`)
            ) ENGINE = InnoDB
              DEFAULT CHARSET = utf8mb4
              COLLATE = utf8mb4_bin;
            """.trimIndent()
        }.rowsUpdated()
    }

    @AfterEach
    fun testDown() = runTest {
        target.sql { +"DROP TABLE sample" }.rowsUpdated()
    }

    @Test
    fun test() = runTest {
        val result = target.sql {
            +"INSERT INTO sample (sample_boolean) VALUES (${bind(true)})"
        }.generatedValues("id")
        assertThat(result).isEqualTo(mapOf("id" to 1L))
    }

    @Test
    fun test2() = runTest {
        val result = target.sql {
            +"INSERT INTO sample (sample_boolean) VALUES (${bind(true)})"
        }.generatedValues("id")
        assertThat(result).isEqualTo(mapOf("id" to 1L))
    }

    companion object {
        @Container
        @JvmStatic
        private val MYSQL = MySQLContainer("mysql:8.0.37")

        private fun connectionFactory(): ConnectionFactory {
            val url = MYSQL.jdbcUrl.replace("jdbc", "r2dbc")
            val options = ConnectionFactoryOptions.parse(url).mutate()
                .option(ConnectionFactoryOptions.USER, MYSQL.username)
                .option(ConnectionFactoryOptions.PASSWORD, MYSQL.password)
                .build()
            return ConnectionFactories.get(options)
        }
    }
}
