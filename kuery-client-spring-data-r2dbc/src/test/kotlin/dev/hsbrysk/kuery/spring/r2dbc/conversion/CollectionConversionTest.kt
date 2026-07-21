package dev.hsbrysk.kuery.spring.r2dbc.conversion

import assertk.assertFailure
import assertk.assertions.isInstanceOf
import dev.hsbrysk.kuery.spring.r2dbc.R2dbcH2ContractDatabase
import dev.hsbrysk.kuery.spring.testing.contract.conversion.CollectionConversionContract
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class CollectionConversionTest : CollectionConversionContract() {
    override val database get() = h2

    @Test
    fun `null element in an IN list is rejected by r2dbc binding`() = runTest {
        // Unlike JDBC (where a null element binds as SQL NULL and simply never matches),
        // R2DBC's Statement.bind rejects null values, so a collection containing null cannot
        // be expanded. This pins the asymmetry.

        // given
        insertThreeRows()

        // when & then
        assertFailure {
            kueryClient.sql {
                val inList = listOf(StringWrapper("text1"), null)
                +"SELECT * FROM converter WHERE text IN ($inList)"
            }.listMap()
        }.isInstanceOf(IllegalArgumentException::class)
    }

    companion object {
        private val h2 = R2dbcH2ContractDatabase()
    }
}
