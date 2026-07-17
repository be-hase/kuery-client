package dev.hsbrysk.kuery.spring.r2dbc

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.single
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EnumConversionTest {
    private val kueryClient = h2.kueryClient()

    enum class SampleEnum {
        HOGE,
    }

    data class Record(val text: SampleEnum)

    @BeforeEach
    fun beforeEach() = runTest {
        h2.setUpForConverterTest()
    }

    @AfterEach
    fun afterEach() = runTest {
        h2.tearDownForConverterTest()
    }

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

    companion object {
        private val h2 = H2TestDatabase()
    }
}
