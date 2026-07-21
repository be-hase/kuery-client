package dev.hsbrysk.kuery.spring.r2dbc.mysql

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import dev.hsbrysk.kuery.core.single
import dev.hsbrysk.kuery.spring.testing.contract.mysql.MySqlScalarFetchContract
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class MySqlScalarFetchTest : MySqlScalarFetchContract() {
    override val database get() = mysql

    @Test
    fun `EXISTS does not map to Boolean`() = runTest {
        // Unlike JDBC, the R2DBC path cannot narrow MySQL's BIGINT 0/1 to Boolean (see also the
        // jdbc-only Boolean cases noted in MySqlSingleBasicTypeTest). Fetch it as Long instead.
        assertFailure {
            kueryClient
                .sql { +"SELECT EXISTS(SELECT 1 FROM scalar_users WHERE user_id = 1)" }
                .single<Boolean>()
        }.isInstanceOf(IllegalArgumentException::class)
    }

    @Test
    fun `EXISTS maps to Long`() = runTest {
        val exists: Long = kueryClient
            .sql { +"SELECT EXISTS(SELECT 1 FROM scalar_users WHERE user_id = 1)" }
            .single()
        assertThat(exists).isEqualTo(1L)
    }

    companion object {
        private val mysql = R2dbcMySqlContractDatabase()
    }
}
