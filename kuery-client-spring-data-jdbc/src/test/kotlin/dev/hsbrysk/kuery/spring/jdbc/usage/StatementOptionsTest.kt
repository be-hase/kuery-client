package dev.hsbrysk.kuery.spring.jdbc.usage

import assertk.assertThat
import assertk.assertions.hasSize
import dev.hsbrysk.kuery.spring.jdbc.JdbcH2ContractDatabase
import dev.hsbrysk.kuery.spring.testing.contract.usage.StatementOptionsContract
import org.junit.jupiter.api.Test

class StatementOptionsTest : StatementOptionsContract() {
    override val database get() = h2

    private val blockingClient = h2.blockingClient()

    @Test
    fun `maxRows limits result size`() {
        val result = blockingClient.sql { +"SELECT * FROM users" }
            .maxRows(5)
            .listMap()
        assertThat(result).hasSize(5)
    }

    @Test
    fun `queryTimeoutSeconds does not break normal query`() {
        val result = blockingClient.sql { +"SELECT * FROM users" }
            .queryTimeoutSeconds(10)
            .listMap()
        assertThat(result).hasSize(10)
    }

    @Test
    fun `chained fetchSize, maxRows, and queryTimeoutSeconds limit results to maxRows`() {
        val result = blockingClient.sql { +"SELECT * FROM users" }
            .fetchSize(3)
            .maxRows(4)
            .queryTimeoutSeconds(10)
            .listMap()
        assertThat(result).hasSize(4)
    }

    @Test
    fun `fetchSize does not break sequence results`() {
        val result = blockingClient.sql { +"SELECT * FROM users" }
            .fetchSize(3)
            .sequenceMap()
            .toList()
        assertThat(result).hasSize(10)
    }

    @Test
    fun `maxRows limits sequence result size`() {
        val result = blockingClient.sql { +"SELECT * FROM users" }
            .maxRows(5)
            .sequenceMap()
            .toList()
        assertThat(result).hasSize(5)
    }

    @Test
    fun `queryTimeoutSeconds does not break sequence`() {
        val result = blockingClient.sql { +"SELECT * FROM users" }
            .queryTimeoutSeconds(10)
            .sequenceMap()
            .toList()
        assertThat(result).hasSize(10)
    }

    @Test
    fun `chained fetchSize, maxRows, and queryTimeoutSeconds limit sequence results to maxRows`() {
        val result = blockingClient.sql { +"SELECT * FROM users" }
            .fetchSize(3)
            .maxRows(4)
            .queryTimeoutSeconds(10)
            .sequenceMap()
            .toList()
        assertThat(result).hasSize(4)
    }

    @Test
    fun `options are applied immutably`() {
        // given
        val base = blockingClient.sql { +"SELECT * FROM users" }
        val limited = base.maxRows(3)

        // when
        val baseResult = base.listMap()
        val limitedResult = limited.listMap()

        // then
        assertThat(baseResult).hasSize(10)
        assertThat(limitedResult).hasSize(3)
    }

    companion object {
        private val h2 = JdbcH2ContractDatabase()
    }
}
