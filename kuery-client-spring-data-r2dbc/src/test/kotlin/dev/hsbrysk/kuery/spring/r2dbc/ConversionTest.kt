package dev.hsbrysk.kuery.spring.r2dbc

import dev.hsbrysk.kuery.core.bind
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.WritingConverter
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
class ConversionTest {
    private val connectionFactory = connectionFactory()
    private val target = SpringR2dbcKueryClient.builder()
        .connectionFactory(connectionFactory)
        .converters(
            listOf(
                StringWrapperToStringConverter(),
                ListStringToStringConverter(),
                StringToStringWrapperConverter(),
            ),
        )
        .build()

    @BeforeEach
    fun setUp() = runTest {
        target.sql {
            +"""
            CREATE TABLE `sample`
            (
                `id` BIGINT AUTO_INCREMENT,
                `sample_varchar1` VARCHAR(255) DEFAULT NULL,
                `sample_varchar2` VARCHAR(255) DEFAULT NULL,
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

    data class StringWrapper(val value: String)

    @WritingConverter
    class StringWrapperToStringConverter : Converter<StringWrapper, String> {
        override fun convert(source: StringWrapper): String {
            return source.value
        }
    }

    class ListStringToStringConverter : Converter<List<String>, String> {
        override fun convert(source: List<String>): String {
            return source.joinToString(",")
        }
    }

    class StringToStringWrapperConverter : Converter<String, StringWrapper> {
        override fun convert(source: String): StringWrapper {
            return StringWrapper(source)
        }
    }

    @Test
    fun test() = runTest {
        target.sql {
//            val array = arrayOf("hoge", "bar")
            +"INSERT INTO sample (sample_varchar1) VALUES (${bind(StringWrapper("hoge"))})"
        }.rowsUpdated()

        val record = target.sql {
            +"SELECT * FROM sample"
        }.single()

        println("hogehoge")
        println(record)
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
