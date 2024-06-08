package dev.hsbrysk.kuery.spring.r2dbc

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.single
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.awaitRowsUpdated

class StringCaseTest {
    private val kueryClient = mysql.kueryClient()

    @BeforeEach
    fun beforeEach() = runTest {
        mysql.databaseClient.sql(
            """
            CREATE TABLE `string_case`
            (
                `id` BIGINT AUTO_INCREMENT,
                `hoge_bar` VARCHAR(255) DEFAULT NULL,
                `fugaPiyo` VARCHAR(255) DEFAULT NULL,
                PRIMARY KEY (`id`)
            ) ENGINE = InnoDB
              DEFAULT CHARSET = utf8mb4
              COLLATE = utf8mb4_bin;
            """.trimIndent(),
        ).fetch().awaitRowsUpdated()
    }

    @AfterEach
    fun afterEach() = runTest {
        mysql.databaseClient.sql("DROP TABLE string_case").fetch().awaitRowsUpdated()
    }

    data class Record(
        val hogeBar: String,
        val fugaPiyo: String,
    )

    @Test
    fun test() = runTest {
        kueryClient.sql {
            +"INSERT INTO string_case (hoge_bar, fugaPiyo) VALUES ('a', 'b')"
        }.rowsUpdated()

        val record: Record = kueryClient.sql {
            +"SELECT * FROM string_case"
        }.single()
        println(record)

        assertThat(record.hogeBar).isEqualTo("a")
        assertThat(record.fugaPiyo).isEqualTo("b")
    }

    companion object {
        private val mysql = MySqlTestContainer()

        @AfterAll
        @JvmStatic
        fun afterAll() {
            mysql.close()
        }
    }
}
