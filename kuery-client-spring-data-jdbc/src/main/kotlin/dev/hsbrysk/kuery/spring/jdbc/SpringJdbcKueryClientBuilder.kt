package dev.hsbrysk.kuery.spring.jdbc

import dev.hsbrysk.kuery.core.KueryBlockingClient
import dev.hsbrysk.kuery.core.observation.KueryClientFetchObservationConvention
import io.micrometer.observation.ObservationRegistry
import javax.sql.DataSource

/**
 * Builder for a [KueryBlockingClient] backed by Spring Data JDBC.
 *
 * Obtain an instance via [SpringJdbcKueryClient.builder]. [dataSource] is required; everything
 * else is optional.
 */
public interface SpringJdbcKueryClientBuilder {
    /**
     * Sets the [DataSource] to execute SQL against. Required.
     */
    public fun dataSource(dataSource: DataSource): SpringJdbcKueryClientBuilder

    /**
     * Sets custom converters used for both binding parameters and mapping rows, typically
     * Spring `Converter`s annotated with `@WritingConverter` / `@ReadingConverter`.
     */
    public fun converters(converters: List<Any>): SpringJdbcKueryClientBuilder

    /**
     * Sets the [ObservationRegistry], enabling Micrometer Observation instrumentation
     * (metrics/tracing) for each fetch.
     */
    public fun observationRegistry(observationRegistry: ObservationRegistry): SpringJdbcKueryClientBuilder

    /**
     * Sets the [KueryClientFetchObservationConvention] used to customize the observation name
     * and tags. When not set, [KueryClientFetchObservationConvention.default] is used.
     */
    public fun observationConvention(
        observationConvention: KueryClientFetchObservationConvention,
    ): SpringJdbcKueryClientBuilder

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
    public fun enableAutoSqlIdGeneration(enableAutoSqlIdGeneration: Boolean): SpringJdbcKueryClientBuilder

    /**
     * Builds the [KueryBlockingClient].
     *
     * @throws IllegalArgumentException if [dataSource] has not been set
     */
    public fun build(): KueryBlockingClient
}
