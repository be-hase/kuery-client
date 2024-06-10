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
     * build [KueryBlockingClient]
     */
    fun build(): KueryBlockingClient
}
