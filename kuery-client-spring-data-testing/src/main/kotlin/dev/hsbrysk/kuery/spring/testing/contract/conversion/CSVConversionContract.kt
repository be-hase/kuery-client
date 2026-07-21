package dev.hsbrysk.kuery.spring.testing.contract.conversion

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.single
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Contract shared by the jdbc and r2dbc modules. Each module runs it via a concrete subclass
 * that supplies its `ContractDatabase`.
 */
abstract class CSVConversionContract : ConverterContractBase() {
    data class Record(val text: List<String>)

    @Test
    fun `comma-separated text is read into a List property with trimmed elements`() = runTest {
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
}
