package dev.hsbrysk.kuery.spring.r2dbc

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import com.example.spring.r2dbc.UserRepository
import dev.hsbrysk.kuery.core.observation.KueryClientFetchContext
import io.micrometer.observation.Observation
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor
import io.micrometer.observation.tck.TestObservationRegistry
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.Result
import io.r2dbc.spi.Statement
import kotlinx.coroutines.reactor.asCoroutineContext
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.reactivestreams.Publisher
import org.springframework.r2dbc.core.awaitRowsUpdated
import reactor.core.publisher.Flux
import reactor.util.context.Context
import java.util.concurrent.CopyOnWriteArrayList

class ObservationReactorContextTest {
    private val registry = TestObservationRegistry.create()
    private val capturedObservations = CopyOnWriteArrayList<Observation?>()
    private val kueryClient = SpringR2dbcKueryClient.builder()
        .connectionFactory(ObservationCapturingConnectionFactory(mysql.connectionFactory, capturedObservations))
        .observationRegistry(registry)
        .enableAutoSqlIdGeneration(true)
        .build()
    private val userRepository = UserRepository(kueryClient)

    @BeforeEach
    fun setUp() = runTest {
        mysql.databaseClient.sql(
            """
            CREATE TABLE users (
                user_id INT AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(50) NOT NULL,
                email VARCHAR(100) NOT NULL
            );

            INSERT INTO users (username, email) VALUES
            ('user1', 'user1@example.com'),
            ('user2', 'user2@example.com');
            """.trimIndent(),
        ).fetch().awaitRowsUpdated()
        capturedObservations.clear()
    }

    @AfterEach
    fun testDown() = runTest {
        mysql.databaseClient.sql(
            """
            DROP TABLE users;
            """.trimIndent(),
        ).fetch().awaitRowsUpdated()
    }

    @Test
    fun singleMap() = runTest {
        userRepository.singleMap(1)
        assertCapturedObservation("com.example.spring.r2dbc.UserRepository.singleMap")
    }

    @Test
    fun singleMapOrNull() = runTest {
        userRepository.singleMapOrNull(1)
        assertCapturedObservation("com.example.spring.r2dbc.UserRepository.singleMapOrNull")
    }

    @Test
    fun single() = runTest {
        userRepository.single(1)
        assertCapturedObservation("com.example.spring.r2dbc.UserRepository.single")
    }

    @Test
    fun singleOrNull() = runTest {
        userRepository.singleOrNull(1)
        assertCapturedObservation("com.example.spring.r2dbc.UserRepository.singleOrNull")
    }

    @Test
    fun listMap() = runTest {
        userRepository.listMap()
        assertCapturedObservation("com.example.spring.r2dbc.UserRepository.listMap")
    }

    @Test
    fun list() = runTest {
        userRepository.list()
        assertCapturedObservation("com.example.spring.r2dbc.UserRepository.list")
    }

    @Test
    fun rowsUpdated() = runTest {
        userRepository.rowUpdated("user3", "user3@example.com")
        assertCapturedObservation("com.example.spring.r2dbc.UserRepository.rowUpdated")
    }

    @Test
    fun generatedValues() = runTest {
        userRepository.generatedValues("user3", "user3@example.com")
        assertCapturedObservation("com.example.spring.r2dbc.UserRepository.generatedValues")
    }

    @Test
    fun `links parent observation from the Reactor context`() = runTest {
        val parent = Observation.start("parent", registry)
        try {
            withContext(Context.of(ObservationThreadLocalAccessor.KEY, parent).asCoroutineContext()) {
                userRepository.singleMap(1)
            }
        } finally {
            parent.stop()
        }

        assertThat(capturedObservations).hasSize(1)
        val observation = assertThat(capturedObservations.single()).isNotNull()
        observation.transform { it.context.parentObservation }.isEqualTo(parent)
    }

    private fun assertCapturedObservation(sqlId: String) {
        assertThat(capturedObservations).hasSize(1)
        val observation = assertThat(capturedObservations.single()).isNotNull()
        observation.transform { it.context }.isInstanceOf(KueryClientFetchContext::class)
            .transform { it.sqlId }.isEqualTo(sqlId)
    }

    private class ObservationCapturingConnectionFactory(
        private val delegate: ConnectionFactory,
        private val captured: MutableList<Observation?>,
    ) : ConnectionFactory by delegate {
        override fun create(): Publisher<out Connection> = Flux.from(delegate.create())
            .map { ObservationCapturingConnection(it, captured) }
    }

    private class ObservationCapturingConnection(
        private val delegate: Connection,
        private val captured: MutableList<Observation?>,
    ) : Connection by delegate {
        override fun createStatement(sql: String): Statement =
            ObservationCapturingStatement(delegate.createStatement(sql), captured)
    }

    // Fluent methods must return `this` (not the delegate) so that execute() below stays wrapped.
    private class ObservationCapturingStatement(
        private val delegate: Statement,
        private val captured: MutableList<Observation?>,
    ) : Statement {
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

        override fun execute(): Publisher<out Result> = Flux.deferContextual { context ->
            captured.add(context.getOrEmpty<Observation>(ObservationThreadLocalAccessor.KEY).orElse(null))
            Flux.from(delegate.execute())
        }
    }

    companion object {
        private val mysql = MySqlTestContainer()

        @AfterAll
        @JvmStatic
        fun afterAll() {
            mysql.close()
        }
    }
}
