package dev.hsbrysk.kuery.spring.r2dbc

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import dev.hsbrysk.kuery.core.single
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.connection.R2dbcTransactionManager
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait

/**
 * The client participates in Spring-managed reactive transactions (the same mechanism behind
 * `@Transactional`) as long as it is built on the transaction manager's ConnectionFactory.
 */
class TransactionTest {
    private val kueryClient = h2.kueryClient()
    private val transactionalOperator = TransactionalOperator.create(R2dbcTransactionManager(h2.connectionFactory))

    @BeforeEach
    fun beforeEach() = runTest {
        h2.setUpForConverterTest()
    }

    @AfterEach
    fun afterEach() = runTest {
        h2.tearDownForConverterTest()
    }

    @Test
    fun `commit persists the changes`() = runTest {
        transactionalOperator.executeAndAwait {
            kueryClient.sql { +"INSERT INTO converter (text) VALUES ('text1')" }.rowsUpdated()
        }

        assertThat(count()).isEqualTo(1L)
    }

    @Test
    fun `setRollbackOnly discards the changes`() = runTest {
        transactionalOperator.executeAndAwait { status ->
            kueryClient.sql { +"INSERT INTO converter (text) VALUES ('text1')" }.rowsUpdated()
            status.setRollbackOnly()
        }

        assertThat(count()).isEqualTo(0L)
    }

    @Test
    fun `an exception rolls back the changes`() = runTest {
        assertFailure {
            transactionalOperator.executeAndAwait<Nothing> {
                kueryClient.sql { +"INSERT INTO converter (text) VALUES ('text1')" }.rowsUpdated()
                error("boom")
            }
        }.isInstanceOf(IllegalStateException::class)

        assertThat(count()).isEqualTo(0L)
    }

    private suspend fun count(): Long = kueryClient.sql { +"SELECT COUNT(*) FROM converter" }.single()

    companion object {
        private val h2 = H2TestDatabase()
    }
}
