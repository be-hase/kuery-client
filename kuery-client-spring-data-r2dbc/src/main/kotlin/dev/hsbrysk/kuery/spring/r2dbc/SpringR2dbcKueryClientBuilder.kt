package dev.hsbrysk.kuery.spring.r2dbc

import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.core.observation.KueryClientFetchObservationConvention
import io.micrometer.observation.ObservationRegistry
import io.r2dbc.spi.ConnectionFactory

interface SpringR2dbcKueryClientBuilder {
    fun connectionFactory(connectionFactory: ConnectionFactory): SpringR2dbcKueryClientBuilder

    fun converters(converters: List<Any>): SpringR2dbcKueryClientBuilder

    fun observationRegistry(observationRegistry: ObservationRegistry): SpringR2dbcKueryClientBuilder

    fun observationConvention(
        observationConvention: KueryClientFetchObservationConvention,
    ): SpringR2dbcKueryClientBuilder

    fun build(): KueryClient
}
