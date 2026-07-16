package dev.hsbrysk.kuery.spring.jdbc

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import dev.hsbrysk.kuery.core.single
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * The client participates in Spring-managed transactions (the same mechanism behind
 * `@Transactional`) as long as it is built on the transaction manager's DataSource.
 */
class TransactionTest {
    private val kueryClient = h2.kueryClient()
    private val transactionTemplate = TransactionTemplate(DataSourceTransactionManager(h2.dataSource))

    @BeforeEach
    fun beforeEach() {
        h2.setUpForConverterTest()
    }

    @AfterEach
    fun afterEach() {
        h2.tearDownForConverterTest()
    }

    @Test
    fun `commit persists the changes`() {
        transactionTemplate.execute {
            kueryClient.sql { +"INSERT INTO converter (text) VALUES ('text1')" }.rowsUpdated()
        }

        assertThat(count()).isEqualTo(1L)
    }

    @Test
    fun `setRollbackOnly discards the changes`() {
        transactionTemplate.execute { status ->
            kueryClient.sql { +"INSERT INTO converter (text) VALUES ('text1')" }.rowsUpdated()
            status.setRollbackOnly()
        }

        assertThat(count()).isEqualTo(0L)
    }

    @Test
    fun `an exception rolls back the changes`() {
        assertFailure {
            transactionTemplate.execute<Nothing> {
                kueryClient.sql { +"INSERT INTO converter (text) VALUES ('text1')" }.rowsUpdated()
                error("boom")
            }
        }.isInstanceOf(IllegalStateException::class)

        assertThat(count()).isEqualTo(0L)
    }

    private fun count(): Long = kueryClient.sql { +"SELECT COUNT(*) FROM converter" }.single()

    companion object {
        private val h2 = H2TestDatabase()
    }
}
