package dev.hsbrysk.kuery.spring.r2dbc.usage

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.flow
import dev.hsbrysk.kuery.spring.r2dbc.H2TestDatabase
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.awaitRowsUpdated

/**
 * flow()/flowMap() are cold: the statement executes when the flow is collected, not when the
 * FetchSpec method returns. The jdbc module pins the opposite (eager) contract for sequence() in
 * SequenceExecutionTimingTest.
 */
class FlowExecutionTimingTest {
    private val kueryClient = h2.kueryClient()

    @BeforeEach
    fun beforeEach() = runTest {
        h2.setUpForConverterTest()
        h2.databaseClient.sql("INSERT INTO converter (text) VALUES ('text1'), ('text2')")
            .fetch().awaitRowsUpdated()
    }

    @AfterEach
    fun afterEach() = runTest {
        h2.tearDownForConverterTest()
    }

    @Test
    fun `flow defers query execution until collected`() = runTest {
        // given
        val flow = kueryClient.sql { +"SELECT text FROM converter ORDER BY id" }.flow<String>()

        // when: a row inserted after the flow was created ...
        h2.databaseClient.sql("INSERT INTO converter (text) VALUES ('text3')").fetch().awaitRowsUpdated()

        // then: ... is visible to the collection, so the query ran at collect time
        assertThat(flow.toList()).isEqualTo(listOf("text1", "text2", "text3"))
    }

    @Test
    fun `each collection of the same flow re-executes the query`() = runTest {
        // given
        val flow = kueryClient.sql { +"SELECT text FROM converter ORDER BY id" }.flow<String>()
        assertThat(flow.toList()).isEqualTo(listOf("text1", "text2"))

        // when
        h2.databaseClient.sql("INSERT INTO converter (text) VALUES ('text3')").fetch().awaitRowsUpdated()

        // then
        assertThat(flow.toList()).isEqualTo(listOf("text1", "text2", "text3"))
    }

    companion object {
        private val h2 = H2TestDatabase()
    }
}
