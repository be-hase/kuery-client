package dev.hsbrysk.kuery.spring.r2dbc

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.single
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CSVConversionTest {
    private val kueryClient = mysql.kueryClient()

    data class Record(val text: List<String>)

    @BeforeEach
    fun beforeEach() = runTest {
        mysql.setUpForConverterTest()
    }

    @AfterEach
    fun afterEach() = runTest {
        mysql.tearDownForConverterTest()
    }

    @Test
    fun test() = runTest {
        val text = "a, b,c"
        kueryClient.sql {
            +"INSERT INTO converter (text) VALUES ($text)"
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
