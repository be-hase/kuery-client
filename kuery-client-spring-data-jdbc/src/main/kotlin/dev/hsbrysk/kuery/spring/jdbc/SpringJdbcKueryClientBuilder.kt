package dev.hsbrysk.kuery.spring.jdbc

import dev.hsbrysk.kuery.core.KueryBlockingClient
import dev.hsbrysk.kuery.core.observation.KueryClientFetchObservationConvention
import io.micrometer.observation.ObservationRegistry
import javax.sql.DataSource

public interface SpringJdbcKueryClientBuilder {
    /**
     * Set [DataSource]
     */
    public fun dataSource(dataSource: DataSource): SpringJdbcKueryClientBuilder

    /**
     * Set converters
     */
    public fun converters(converters: List<Any>): SpringJdbcKueryClientBuilder

    /**
     * Set [ObservationRegistry]
     */
    public fun observationRegistry(observationRegistry: ObservationRegistry): SpringJdbcKueryClientBuilder

    /**
     * Set [KueryClientFetchObservationConvention]
     */
    public fun observationConvention(
        observationConvention: KueryClientFetchObservationConvention,
    ): SpringJdbcKueryClientBuilder

    /**
     * It is a flag to automatically generate a sqlId for metrics.
     * When [observationRegistry] is specified, the default is true; otherwise, the default is false.
     *
     * The sqlId is derived from the call site of the first invocation and cached per SQL block.
     * If the same block (e.g. one stored in a property) is shared across multiple call sites,
     * all of them observe the sqlId of whichever call site ran first.
     * Define blocks inline if each call site should get its own sqlId.
     */
    public fun enableAutoSqlIdGeneration(enableAutoSqlIdGeneration: Boolean): SpringJdbcKueryClientBuilder

    /**
     * build [KueryBlockingClient]
     */
    public fun build(): KueryBlockingClient
}
