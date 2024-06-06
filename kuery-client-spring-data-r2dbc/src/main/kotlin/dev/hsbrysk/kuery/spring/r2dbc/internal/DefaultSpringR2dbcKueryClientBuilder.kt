package dev.hsbrysk.kuery.spring.r2dbc.internal

import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.spring.r2dbc.SpringR2dbcKueryClientBuilder
import io.r2dbc.spi.ConnectionFactory
import org.springframework.core.convert.support.DefaultConversionService
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions
import org.springframework.data.r2dbc.dialect.DialectResolver
import org.springframework.r2dbc.core.DatabaseClient

internal class DefaultSpringR2dbcKueryClientBuilder : SpringR2dbcKueryClientBuilder {
    private var connectionFactory: ConnectionFactory? = null
    private var converters: List<Any> = emptyList()
    private var databaseClient: DatabaseClient? = null

    override fun connectionFactory(connectionFactory: ConnectionFactory): SpringR2dbcKueryClientBuilder {
        this.connectionFactory = connectionFactory
        return this
    }

    override fun converters(converters: List<Any>): SpringR2dbcKueryClientBuilder {
        this.converters = converters
        return this
    }

    override fun databaseClient(databaseClient: DatabaseClient): SpringR2dbcKueryClientBuilder {
        this.databaseClient = databaseClient
        return this
    }

    override fun build(): KueryClient {
        val databaseClient = this.databaseClient ?: defaultDatabaseClient()
        val conversionService = DefaultConversionService()
        val customConversions = r2dbcCustomConversions()
        customConversions.registerConvertersIn(conversionService)
        return DefaultSpringR2dbcKueryClient(databaseClient, conversionService, customConversions)
    }

    private fun defaultDatabaseClient(): DatabaseClient {
        val connectionFactory = requireNotNull(this.connectionFactory) {
            "Specify either databaseClient or connectionFactory."
        }
        return DatabaseClient.builder()
            .connectionFactory(connectionFactory)
            .bindMarkers(DialectResolver.getDialect(connectionFactory).bindMarkersFactory)
            .build()
    }

    private fun r2dbcCustomConversions(): R2dbcCustomConversions {
        val connectionFactory = requireNotNull(databaseClient?.connectionFactory ?: connectionFactory) {
            "Specify either databaseClient or connectionFactory."
        }
        return R2dbcCustomConversions.of(DialectResolver.getDialect(connectionFactory), converters)
    }
}
