package dev.hsbrysk.kuery.spring.r2dbc

import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.core.observation.KueryClientFetchObservationConvention
import io.micrometer.observation.ObservationRegistry
import io.r2dbc.spi.ConnectionFactory

/**
 * Builder for a [KueryClient] backed by Spring Data R2DBC.
 *
 * Obtain an instance via [SpringR2dbcKueryClient.builder]. [connectionFactory] is required;
 * everything else is optional.
 */
public interface SpringR2dbcKueryClientBuilder {
    /**
     * Sets the [ConnectionFactory] to execute SQL against. Required.
     */
    public fun connectionFactory(connectionFactory: ConnectionFactory): SpringR2dbcKueryClientBuilder

    /**
     * Sets custom converters used for both binding parameters and mapping rows, typically
     * Spring `Converter`s annotated with `@WritingConverter` / `@ReadingConverter`.
     */
    public fun converters(converters: List<Any>): SpringR2dbcKueryClientBuilder

    /**
     * Sets the [ObservationRegistry], enabling Micrometer Observation instrumentation
     * (metrics/tracing) for each fetch.
     */
    public fun observationRegistry(observationRegistry: ObservationRegistry): SpringR2dbcKueryClientBuilder

    /**
     * Sets the [KueryClientFetchObservationConvention] used to customize the observation name
     * and tags. When not set, [KueryClientFetchObservationConvention.default] is used.
     */
    public fun observationConvention(
        observationConvention: KueryClientFetchObservationConvention,
    ): SpringR2dbcKueryClientBuilder

    /**
     * Sets whether to automatically generate a sqlId for metrics when `sql { ... }` is called
     * without an explicit sqlId.
     * When [observationRegistry] is specified, the default is true; otherwise, the default is false.
     *
     * The sqlId is generated at compile time by the compiler plugin from the fully qualified name
     * of the call site's enclosing declaration (e.g. `com.example.UserRepository.selectByUserId`),
     * so no runtime stack inspection is involved and a given call site always produces the same
     * sqlId. Blocks passed via a variable and call sites not compiled with the compiler plugin
     * resolve to the fixed sqlId `NONE`; specify the sqlId explicitly in such cases.
     */
    public fun enableAutoSqlIdGeneration(enableAutoSqlIdGeneration: Boolean): SpringR2dbcKueryClientBuilder

    /**
     * Builds the [KueryClient].
     *
     * @throws IllegalArgumentException if [connectionFactory] has not been set
     */
    public fun build(): KueryClient
}
