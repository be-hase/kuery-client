package dev.hsbrysk.kuery.spring.jdbc

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.single
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StringCaseTest {
    private val kueryClient = mysql.kueryClient()

    @BeforeEach
    fun beforeEach() {
        mysql.jdbcClient().sql(
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
        ).update()
    }

    @AfterEach
    fun afterEach() {
        mysql.jdbcClient().sql("DROP TABLE string_case").update()
    }

    data class Record(
        val hogeBar: String,
        val fugaPiyo: String,
    )

    @Test
    fun test() {
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
