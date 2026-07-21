package dev.hsbrysk.kuery.spring.jdbc.conversion

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.spring.jdbc.JdbcH2ContractDatabase
import dev.hsbrysk.kuery.spring.testing.contract.conversion.CollectionConversionContract
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class CollectionConversionTest : CollectionConversionContract() {
    override val database get() = h2

    @Test
    fun `null element in an IN list binds as SQL NULL and never matches`() = runTest {
        // A null element binds as SQL NULL, which never matches in an IN list; the non-null
        // elements still do.

        // given
        insertThreeRows()

        // when & then
        val result = kueryClient.sql {
            val inList = listOf(StringWrapper("text1"), null)
            +"SELECT * FROM converter WHERE text IN ($inList)"
        }.listMap()
        assertThat(result).isEqualTo(listOf(mapOf("id" to 1L, "text" to "text1")))
    }

    companion object {
        private val h2 = JdbcH2ContractDatabase()
    }
}
