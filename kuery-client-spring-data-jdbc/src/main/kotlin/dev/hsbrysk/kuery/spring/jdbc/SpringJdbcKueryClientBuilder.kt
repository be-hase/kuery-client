package dev.hsbrysk.kuery.spring.jdbc

import dev.hsbrysk.kuery.core.KueryBlockingClient
import dev.hsbrysk.kuery.core.observation.KueryClientFetchObservationConvention
import io.micrometer.observation.ObservationRegistry
import javax.sql.DataSource

interface SpringJdbcKueryClientBuilder {
    fun dataSource(dataSource: DataSource): SpringJdbcKueryClientBuilder

    fun converters(converters: List<Any>): SpringJdbcKueryClientBuilder

    fun observationRegistry(observationRegistry: ObservationRegistry): SpringJdbcKueryClientBuilder

    fun observationConvention(
        observationConvention: KueryClientFetchObservationConvention,
    ): SpringJdbcKueryClientBuilder

    fun build(): KueryBlockingClient
}
