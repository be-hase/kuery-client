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
abstract class EnumConversionContract : ConverterContractBase() {
    enum class SampleEnum {
        HOGE,
    }

    data class Record(val text: SampleEnum)

    @Test
    fun `enum is stored as its name and mapped back to the enum`() = runTest {
        // given
        kueryClient.sql {
            +"INSERT INTO converter (text) VALUES (${SampleEnum.HOGE})"
        }.rowsUpdated()

        // when & then
        val record: Record = kueryClient.sql {
            +"SELECT * FROM converter"
        }.single()
        assertThat(record.text).isEqualTo(SampleEnum.HOGE)

        val map = kueryClient.sql {
            +"SELECT * FROM converter"
        }.singleMap()
        assertThat(map["text"]).isEqualTo("HOGE")
    }

    @Test
    fun `enum is converted when fetched as a scalar`() = runTest {
        // given
        kueryClient.sql {
            +"INSERT INTO converter (text) VALUES (${SampleEnum.HOGE})"
        }.rowsUpdated()

        // when & then
        val result: SampleEnum = kueryClient.sql {
            +"SELECT text FROM converter"
        }.single()
        assertThat(result).isEqualTo(SampleEnum.HOGE)
    }

    @Test
    fun `enum in an IN clause matches the row stored by name`() = runTest {
        // given
        kueryClient.sql {
            +"INSERT INTO converter (text) VALUES (${SampleEnum.HOGE})"
        }.rowsUpdated()

        // when & then
        val record: Record = kueryClient.sql {
            +"SELECT * FROM converter WHERE text IN (${listOf(SampleEnum.HOGE)})"
        }.single()
        assertThat(record.text).isEqualTo(SampleEnum.HOGE)
    }

    @Test
    fun `enum in a composite IN tuple matches the row stored by name`() = runTest {
        // given
        kueryClient.sql {
            +"INSERT INTO converter (text) VALUES (${SampleEnum.HOGE})"
        }.rowsUpdated()

        // when & then
        val record: Record = kueryClient.sql {
            val pairs = listOf(arrayOf<Any>(1L, SampleEnum.HOGE))
            +"SELECT * FROM converter WHERE (id, text) IN ($pairs)"
        }.single()
        assertThat(record.text).isEqualTo(SampleEnum.HOGE)
    }
}
