package dev.hsbrysk.kuery.spring.r2dbc

import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.core.observation.KueryClientFetchObservationConvention
import io.micrometer.observation.ObservationRegistry
import io.r2dbc.spi.ConnectionFactory

public interface SpringR2dbcKueryClientBuilder {
    /**
     * Set [ConnectionFactory]
     */
    public fun connectionFactory(connectionFactory: ConnectionFactory): SpringR2dbcKueryClientBuilder

    /**
     * Set converters
     */
    public fun converters(converters: List<Any>): SpringR2dbcKueryClientBuilder

    /**
     * Set [ObservationRegistry]
     */
    public fun observationRegistry(observationRegistry: ObservationRegistry): SpringR2dbcKueryClientBuilder

    /**
     * Set [KueryClientFetchObservationConvention]
     */
    public fun observationConvention(
        observationConvention: KueryClientFetchObservationConvention,
    ): SpringR2dbcKueryClientBuilder

    /**
     * It is a flag to automatically generate a sqlId for metrics.
     * When [observationRegistry] is specified, the default is true; otherwise, the default is false.
     *
     * The sqlId is derived from the call site of the first invocation and cached per SQL block.
     * If the same block (e.g. one stored in a property) is shared across multiple call sites,
     * all of them observe the sqlId of whichever call site ran first.
     * Define blocks inline if each call site should get its own sqlId.
     */
    public fun enableAutoSqlIdGeneration(enableAutoSqlIdGeneration: Boolean): SpringR2dbcKueryClientBuilder

    /**
     * Build [KueryClient]
     */
    public fun build(): KueryClient
}
