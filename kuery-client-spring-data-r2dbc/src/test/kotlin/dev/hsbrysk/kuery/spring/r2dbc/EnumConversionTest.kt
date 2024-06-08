package dev.hsbrysk.kuery.spring.r2dbc

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.single
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class EnumConversionTest : MySQLTestContainersBase() {
    override fun converters(): List<Any> {
        return listOf()
    }

    enum class SampleEnum {
        HOGE,
    }

    data class Record(
        val text: SampleEnum,
    )

    @Test
    fun test() = runTest {
        kueryClient.sql {
            +"INSERT INTO converter (text) VALUES (${bind(SampleEnum.HOGE)})"
        }.rowsUpdated()

        val record: Record = kueryClient.sql {
            +"SELECT * FROM converter"
        }.single()

        assertThat(record.text).isEqualTo(SampleEnum.HOGE.name)
    }
}
