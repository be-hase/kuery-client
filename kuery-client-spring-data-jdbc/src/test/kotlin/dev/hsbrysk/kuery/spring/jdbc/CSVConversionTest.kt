package dev.hsbrysk.kuery.spring.jdbc

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.single
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CSVConversionTest {
    private val kueryClient = h2.kueryClient()

    data class Record(val text: List<String>)

    @BeforeEach
    fun beforeEach() {
        h2.setUpForConverterTest()
    }

    @AfterEach
    fun afterEach() {
        h2.tearDownForConverterTest()
    }

    @Test
    fun `comma-separated text is read into a List property with trimmed elements`() {
        // given
        val text = "a, b,c"
        kueryClient.sql {
            +"INSERT INTO converter (text) VALUES ($text)"
        }.rowsUpdated()

        // when
        val record: Record = kueryClient.sql {
            +"SELECT * FROM converter"
        }.single()

        // then
        assertThat(record.text).isEqualTo(listOf("a", "b", "c"))
    }

    companion object {
        private val h2 = H2TestDatabase()
    }
}
