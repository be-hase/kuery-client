package dev.hsbrysk.kuery.spring.r2dbc.observation

import com.example.spring.r2dbc.UserRepository
import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.core.observation.KueryClientFetchObservationConvention
import dev.hsbrysk.kuery.spring.r2dbc.R2dbcH2ContractDatabase
import dev.hsbrysk.kuery.spring.r2dbc.SpringR2dbcKueryClient
import dev.hsbrysk.kuery.spring.testing.contract.observation.ObservationContract
import io.micrometer.observation.ObservationRegistry
import io.micrometer.observation.tck.TestObservationRegistryAssert
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.Result
import io.r2dbc.spi.Statement
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux

class ObservationTest : ObservationContract() {
    override val database get() = h2

    override fun conventionKueryClient(
        observationRegistry: ObservationRegistry,
        observationConvention: KueryClientFetchObservationConvention,
    ): KueryClient = SpringR2dbcKueryClient.builder()
        .connectionFactory(h2.connectionFactory)
        .observationRegistry(observationRegistry)
        .observationConvention(observationConvention)
        .build()

    // Streaming via Flow and cancellation are r2dbc-specific, so they are tested here against
    // the real API rather than through the contract.

    @Test
    fun `flowMap does not record an observation`() = runTest {
        UserRepository(kueryClient).flowMap().collect()
        TestObservationRegistryAssert.assertThat(registry).doesNotHaveAnyObservation()
    }

    @Test
    fun `flow does not record an observation`() = runTest {
        UserRepository(kueryClient).flow().collect()
        TestObservationRegistryAssert.assertThat(registry).doesNotHaveAnyObservation()
    }

    @Test
    fun `stops the observation without error when the fetch is cancelled`() = runTest {
        // given
        val neverClient = SpringR2dbcKueryClient.builder()
            .connectionFactory(NeverExecutingConnectionFactory(h2.connectionFactory))
            .observationRegistry(registry)
            .build()

        // when
        // UNDISPATCHED runs the coroutine up to its first suspension point, so the fetch is
        // guaranteed to have subscribed (and the observation to have started) before we cancel.
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            neverClient.sql { +"SELECT * FROM users" }.singleMap()
        }
        job.cancelAndJoin()

        // then
        TestObservationRegistryAssert.assertThat(registry)
            .doesNotHaveAnyRemainingCurrentObservation()
            .hasObservationWithNameEqualTo("kuery.client.fetches")
            .that()
            .hasBeenStarted()
            .hasBeenStopped()
            .doesNotHaveError()
    }

    private class NeverExecutingConnectionFactory(private val delegate: ConnectionFactory) :
        ConnectionFactory by delegate {
        override fun create(): Publisher<out Connection> = Flux.from(delegate.create())
            .map { NeverExecutingConnection(it) }
    }

    private class NeverExecutingConnection(private val delegate: Connection) : Connection by delegate {
        override fun createStatement(sql: String): Statement = NeverExecutingStatement(delegate.createStatement(sql))
    }

    // Fluent methods must return `this` (not the delegate) so that execute() below stays wrapped.
    private class NeverExecutingStatement(private val delegate: Statement) : Statement {
        override fun add(): Statement = apply { delegate.add() }

        override fun bind(
            index: Int,
            value: Any,
        ): Statement = apply { delegate.bind(index, value) }

        override fun bind(
            name: String,
            value: Any,
        ): Statement = apply { delegate.bind(name, value) }

        override fun bindNull(
            index: Int,
            type: Class<*>,
        ): Statement = apply { delegate.bindNull(index, type) }

        override fun bindNull(
            name: String,
            type: Class<*>,
        ): Statement = apply { delegate.bindNull(name, type) }

        override fun fetchSize(rows: Int): Statement = apply { delegate.fetchSize(rows) }

        override fun returnGeneratedValues(vararg columns: String): Statement =
            apply { delegate.returnGeneratedValues(*columns) }

        override fun execute(): Publisher<out Result> = Flux.never()
    }

    companion object {
        private val h2 = R2dbcH2ContractDatabase()
    }
}
