package dev.hsbrysk.kuery.spring.jdbc.usage

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.spring.jdbc.H2TestDatabase
import dev.hsbrysk.kuery.spring.jdbc.SpringJdbcKueryClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

/**
 * sequence()/sequenceMap() are NOT lazy: the statement executes eagerly when the sequence is
 * created (see the note in DefaultSpringJdbcKueryClient), before any element is consumed. This
 * pins that contract — wrappers that want to defer or guard execution cannot rely on laziness.
 */
class SequenceExecutionTimingTest {
    private val connectionCount = AtomicInteger()
    private val countingDataSource = object : DataSource by h2.dataSource {
        override fun getConnection(): Connection {
            connectionCount.incrementAndGet()
            return h2.dataSource.connection
        }
    }
    private val kueryClient = SpringJdbcKueryClient.builder()
        .dataSource(countingDataSource)
        .build()

    @BeforeEach
    fun beforeEach() {
        h2.setUpForConverterTest()
        h2.jdbcClient.sql("INSERT INTO converter (text) VALUES ('text1'), ('text2')").update()
        connectionCount.set(0)
    }

    @AfterEach
    fun afterEach() {
        h2.tearDownForConverterTest()
    }

    @Test
    fun `sequenceMap executes the query eagerly at call time`() {
        // when
        val sequence = kueryClient.sql { +"SELECT * FROM converter" }.sequenceMap()

        // then
        sequence.use {
            // The connection was already acquired (and the statement executed) by sequenceMap()
            // itself, before the first element was consumed.
            assertThat(connectionCount.get()).isEqualTo(1)

            assertThat(it.toList()).hasSize(2)
            assertThat(connectionCount.get()).isEqualTo(1)
        }
    }

    companion object {
        private val h2 = H2TestDatabase()
    }
}
