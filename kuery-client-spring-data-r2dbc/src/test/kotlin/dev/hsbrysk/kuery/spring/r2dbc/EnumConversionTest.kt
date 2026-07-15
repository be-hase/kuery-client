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
    fun test() = runTest {
        kueryClient.sql {
            +"INSERT INTO converter (text) VALUES (${SampleEnum.HOGE})"
        }.rowsUpdated()

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
    fun testScalar() = runTest {
        kueryClient.sql {
            +"INSERT INTO converter (text) VALUES (${SampleEnum.HOGE})"
        }.rowsUpdated()

        val result: SampleEnum = kueryClient.sql {
            +"SELECT text FROM converter"
        }.single()
        assertThat(result).isEqualTo(SampleEnum.HOGE)
    }

    @Test
    fun testInCollection() = runTest {
        kueryClient.sql {
            +"INSERT INTO converter (text) VALUES (${SampleEnum.HOGE})"
        }.rowsUpdated()

        val record: Record = kueryClient.sql {
            +"SELECT * FROM converter WHERE text IN (${listOf(SampleEnum.HOGE)})"
        }.single()
        assertThat(record.text).isEqualTo(SampleEnum.HOGE)
    }

    companion object {
        private val h2 = H2TestDatabase()
    }
}
