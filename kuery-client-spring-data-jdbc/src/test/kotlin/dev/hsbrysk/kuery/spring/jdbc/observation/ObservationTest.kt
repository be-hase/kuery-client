package dev.hsbrysk.kuery.spring.jdbc.observation

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.example.spring.jdbc.UserRepository
import dev.hsbrysk.kuery.spring.jdbc.JdbcH2ContractDatabase
import dev.hsbrysk.kuery.spring.testing.contract.observation.ObservationContract
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationHandler
import io.micrometer.observation.tck.TestObservationRegistry
import io.micrometer.observation.tck.TestObservationRegistryAssert
import org.junit.jupiter.api.Test

class ObservationTest : ObservationContract() {
    override val database get() = h2

    // Streaming via Sequence and the Micrometer scope lifecycle are jdbc-specific, so they are
    // tested here against the real blocking API rather than through the contract.

    @Test
    fun `sequenceMap does not record an observation`() {
        val blockingClient = h2.blockingClient(observationRegistry = registry)
        UserRepository(blockingClient).sequenceMap().toList()
        TestObservationRegistryAssert.assertThat(registry).doesNotHaveAnyObservation()
    }

    @Test
    fun `sequence does not record an observation`() {
        val blockingClient = h2.blockingClient(observationRegistry = registry)
        UserRepository(blockingClient).sequence().toList()
        TestObservationRegistryAssert.assertThat(registry).doesNotHaveAnyObservation()
    }

    @Test
    fun `scope is closed before the observation is stopped`() {
        // The documented Micrometer lifecycle order: close the scope, then stop the observation.

        // given
        val events = mutableListOf<String>()
        val recordingRegistry = TestObservationRegistry.create().apply {
            observationConfig().observationHandler(object : ObservationHandler<Observation.Context> {
                override fun supportsContext(context: Observation.Context): Boolean = true

                override fun onScopeClosed(context: Observation.Context) {
                    events.add("scopeClosed")
                }

                override fun onStop(context: Observation.Context) {
                    events.add("stop")
                }
            })
        }
        val client = h2.blockingClient(observationRegistry = recordingRegistry)

        // when
        UserRepository(client).singleMap(1)

        // then
        assertThat(events).isEqualTo(listOf("scopeClosed", "stop"))
    }

    companion object {
        private val h2 = JdbcH2ContractDatabase()
    }
}
