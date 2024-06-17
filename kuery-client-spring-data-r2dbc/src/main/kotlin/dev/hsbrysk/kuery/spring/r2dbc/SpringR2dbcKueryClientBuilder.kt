package dev.hsbrysk.kuery.spring.r2dbc

import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.core.observation.KueryClientFetchObservationConvention
import io.micrometer.observation.ObservationRegistry
import io.r2dbc.spi.ConnectionFactory

interface SpringR2dbcKueryClientBuilder {
    /**
     * Set [ConnectionFactory]
     */
    fun connectionFactory(connectionFactory: ConnectionFactory): SpringR2dbcKueryClientBuilder

    /**
     * Set converters
     */
    fun converters(converters: List<Any>): SpringR2dbcKueryClientBuilder

    /**
     * Set [ObservationRegistry]
     */
    fun observationRegistry(observationRegistry: ObservationRegistry): SpringR2dbcKueryClientBuilder

    /**
     * Set [KueryClientFetchObservationConvention]
     */
    fun observationConvention(
        observationConvention: KueryClientFetchObservationConvention,
    ): SpringR2dbcKueryClientBuilder

    /**
     * It is a flag to automatically generate a sqlId for metrics.
     * When [observationRegistry] is specified, the default is true; otherwise, the default is false.
     */
    fun enableAutoSqlIdGeneration(enableAutoSqlIdGeneration: Boolean): SpringR2dbcKueryClientBuilder

    /**
     * Build [KueryClient]
     */
    fun build(): KueryClient
}
