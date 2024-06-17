package dev.hsbrysk.kuery.spring.jdbc

import dev.hsbrysk.kuery.core.KueryBlockingClient
import dev.hsbrysk.kuery.core.observation.KueryClientFetchObservationConvention
import io.micrometer.observation.ObservationRegistry
import javax.sql.DataSource

interface SpringJdbcKueryClientBuilder {
    /**
     * Set [DataSource]
     */
    fun dataSource(dataSource: DataSource): SpringJdbcKueryClientBuilder

    /**
     * Set converters
     */
    fun converters(converters: List<Any>): SpringJdbcKueryClientBuilder

    /**
     * Set [ObservationRegistry]
     */
    fun observationRegistry(observationRegistry: ObservationRegistry): SpringJdbcKueryClientBuilder

    /**
     * Set [KueryClientFetchObservationConvention]
     */
    fun observationConvention(
        observationConvention: KueryClientFetchObservationConvention,
    ): SpringJdbcKueryClientBuilder

    /**
     * It is a flag to automatically generate a sqlId for metrics.
     * When [observationRegistry] is specified, the default is true; otherwise, the default is false.
     */
    fun enableAutoSqlIdGeneration(enableAutoSqlIdGeneration: Boolean): SpringJdbcKueryClientBuilder

    /**
     * build [KueryBlockingClient]
     */
    fun build(): KueryBlockingClient
}
