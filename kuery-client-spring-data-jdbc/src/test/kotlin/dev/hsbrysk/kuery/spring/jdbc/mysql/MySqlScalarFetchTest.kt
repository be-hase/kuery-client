package dev.hsbrysk.kuery.spring.jdbc.mysql

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.single
import dev.hsbrysk.kuery.spring.testing.contract.mysql.MySqlScalarFetchContract
import org.junit.jupiter.api.Test

class MySqlScalarFetchTest : MySqlScalarFetchContract() {
    override val database get() = mysql

    private val blockingClient = MySqlTestContainer.kueryClient()

    @Test
    fun `EXISTS maps to Boolean`() {
        val exists: Boolean = blockingClient
            .sql { +"SELECT EXISTS(SELECT 1 FROM scalar_users WHERE user_id = 1)" }
            .single()
        assertThat(exists).isEqualTo(true)

        val notExists: Boolean = blockingClient
            .sql { +"SELECT EXISTS(SELECT 1 FROM scalar_users WHERE user_id = 999)" }
            .single()
        assertThat(notExists).isEqualTo(false)
    }

    companion object {
        private val mysql = JdbcMySqlContractDatabase()
    }
}
