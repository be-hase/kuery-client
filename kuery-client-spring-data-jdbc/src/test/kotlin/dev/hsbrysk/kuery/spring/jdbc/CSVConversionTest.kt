package dev.hsbrysk.kuery.spring.jdbc

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.single
import org.junit.jupiter.api.Test

class CSVConversionTest : MySQLTestContainersBase() {
    override fun converters(): List<Any> {
        return listOf()
    }

    data class Record(
        val text: List<String>,
    )

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
}
