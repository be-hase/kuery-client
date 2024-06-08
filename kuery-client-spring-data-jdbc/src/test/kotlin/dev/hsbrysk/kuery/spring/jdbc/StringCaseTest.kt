package dev.hsbrysk.kuery.spring.jdbc

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.single
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StringCaseTest : MySQLTestContainersBase() {
    override fun converters(): List<Any> {
        return listOf()
    }

    @BeforeEach
    fun beforeEach() {
        jdbcClient.sql(
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
        jdbcClient.sql("DROP TABLE string_case").update()
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
}
