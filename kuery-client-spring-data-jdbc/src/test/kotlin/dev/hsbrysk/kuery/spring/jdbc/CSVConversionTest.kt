package dev.hsbrysk.kuery.spring.jdbc

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.single
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CSVConversionTest {
    private val kueryClient = mysql.kueryClient()

    data class Record(
        val text: List<String>,
    )

    @BeforeEach
    fun beforeEach() {
        mysql.setUpForConverterTest()
    }

    @AfterEach
    fun afterEach() {
        mysql.tearDownForConverterTest()
    }

    @Test
    fun test() {
        kueryClient.sql {
            +"INSERT INTO converter (text) VALUES (${bind("a, b,c")})"
        }.rowsUpdated()

        val record: Record = kueryClient.sql {
            +"SELECT * FROM converter"
        }.single()

        assertThat(record.text).isEqualTo(listOf("a", "b", "c"))
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
