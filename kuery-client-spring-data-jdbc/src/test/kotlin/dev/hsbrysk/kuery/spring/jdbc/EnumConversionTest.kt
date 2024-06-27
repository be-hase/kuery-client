package dev.hsbrysk.kuery.spring.jdbc

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.single
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EnumConversionTest {
    private val kueryClient = mysql.kueryClient()

    enum class SampleEnum {
        HOGE,
    }

    data class Record(val text: SampleEnum)

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

    companion object {
        private val mysql = MySqlTestContainer()

        @AfterAll
        @JvmStatic
        fun afterAll() {
            mysql.close()
        }
    }
}
