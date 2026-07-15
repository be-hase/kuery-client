package dev.hsbrysk.kuery.spring.r2dbc.mysql

import assertk.assertThat
import assertk.assertions.isEmpty
import io.micrometer.observation.Observation
import io.micrometer.observation.tck.TestObservationRegistry
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors

/**
 * Exercised against MySQL (not H2) because it needs a query that actually suspends the calling
 * coroutine (`SELECT SLEEP(1)` over a network connection). r2dbc-h2 completes queries synchronously
 * on the calling thread, which would leave no suspension window to observe.
 */
class MySqlObservationLeakTest {
    private val registry = TestObservationRegistry.create()
    private val kueryClient = mysql.kueryClient(observationRegistry = registry)

    @Test
    fun `does not leak observation to sibling coroutines on the same thread`() {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            runBlocking(dispatcher) {
                val query = launch {
                    kueryClient.sql { +"SELECT SLEEP(1)" }.singleMap()
                }
                // While the query coroutine is suspended in awaitXxx (guaranteed by SLEEP(1)),
                // sample the ThreadLocal current observation from a sibling coroutine on the same thread.
                val leaked = mutableListOf<Observation>()
                repeat(8) {
                    delay(100)
                    registry.currentObservation?.let { leaked.add(it) }
                }
                query.join()
                assertThat(leaked).isEmpty()
            }
        }
    }

    companion object {
        private val mysql = MySqlTestContainer
    }
}
