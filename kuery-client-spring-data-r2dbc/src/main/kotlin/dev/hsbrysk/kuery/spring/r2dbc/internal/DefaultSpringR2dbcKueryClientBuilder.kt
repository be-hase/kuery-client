package dev.hsbrysk.kuery.spring.r2dbc.internal

import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.core.observation.KueryClientFetchObservationConvention
import dev.hsbrysk.kuery.spring.r2dbc.SpringR2dbcKueryClientBuilder
import io.micrometer.observation.ObservationRegistry
import io.r2dbc.spi.ConnectionFactory
import org.springframework.core.convert.support.DefaultConversionService
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions
import org.springframework.data.r2dbc.dialect.DialectResolver
import org.springframework.r2dbc.core.DatabaseClient

internal class DefaultSpringR2dbcKueryClientBuilder : SpringR2dbcKueryClientBuilder {
    private var connectionFactory: ConnectionFactory? = null
    private var converters: List<Any> = emptyList()
    private var observationRegistry: ObservationRegistry? = null
    private var observationConvention: KueryClientFetchObservationConvention? = null

    override fun connectionFactory(connectionFactory: ConnectionFactory): SpringR2dbcKueryClientBuilder {
        this.connectionFactory = connectionFactory
        return this
    }

    override fun converters(converters: List<Any>): SpringR2dbcKueryClientBuilder {
        this.converters = converters
        return this
    }

    override fun observationRegistry(observationRegistry: ObservationRegistry): SpringR2dbcKueryClientBuilder {
        this.observationRegistry = observationRegistry
        return this
    }

    override fun observationConvention(
        observationConvention: KueryClientFetchObservationConvention,
    ): SpringR2dbcKueryClientBuilder {
        this.observationConvention = observationConvention
        return this
    }

    override fun build(): KueryClient {
        val connectionFactory = requireNotNull(this.connectionFactory) {
            "Specify connectionFactory."
        }

        val databaseClient = databaseClient(connectionFactory)
        val conversionService = DefaultConversionService()
        val customConversions = r2dbcCustomConversions(connectionFactory).apply {
            registerConvertersIn(conversionService)
        }

        return DefaultSpringR2dbcKueryClient(
            databaseClient,
            conversionService,
            customConversions,
            observationRegistry,
            observationConvention,
        )
    }

    private fun databaseClient(connectionFactory: ConnectionFactory): DatabaseClient {
        return DatabaseClient.builder()
            .connectionFactory(connectionFactory)
            .bindMarkers(DialectResolver.getDialect(connectionFactory).bindMarkersFactory)
            .build()
    }

    private fun r2dbcCustomConversions(connectionFactory: ConnectionFactory): R2dbcCustomConversions {
        return R2dbcCustomConversions.of(DialectResolver.getDialect(connectionFactory), converters)
    }
}
