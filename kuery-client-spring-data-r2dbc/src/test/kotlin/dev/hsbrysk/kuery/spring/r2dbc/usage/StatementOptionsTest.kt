package dev.hsbrysk.kuery.spring.r2dbc.usage

import assertk.assertThat
import assertk.assertions.hasSize
import dev.hsbrysk.kuery.spring.r2dbc.R2dbcH2ContractDatabase
import dev.hsbrysk.kuery.spring.testing.contract.usage.StatementOptionsContract
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class StatementOptionsTest : StatementOptionsContract() {
    override val database get() = h2

    @Test
    fun `options are applied immutably`() = runTest {
        // given
        val base = kueryClient.sql { +"SELECT * FROM users" }
        val withFetchSize = base.fetchSize(3)

        // when
        val baseResult = base.listMap()
        val withFetchSizeResult = withFetchSize.listMap()

        // then
        assertThat(baseResult).hasSize(10)
        assertThat(withFetchSizeResult).hasSize(10)
    }

    companion object {
        private val h2 = R2dbcH2ContractDatabase()
    }
}
