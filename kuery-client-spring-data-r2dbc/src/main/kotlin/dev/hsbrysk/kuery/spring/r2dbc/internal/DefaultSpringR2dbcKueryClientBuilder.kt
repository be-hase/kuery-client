package dev.hsbrysk.kuery.spring.r2dbc.internal

import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.spring.r2dbc.SpringR2dbcKueryClientBuilder
import io.r2dbc.spi.ConnectionFactory
import org.springframework.core.convert.ConversionService
import org.springframework.core.convert.support.DefaultConversionService
import org.springframework.data.convert.CustomConversions.StoreConversions
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions
import org.springframework.data.r2dbc.dialect.DialectResolver
import org.springframework.data.r2dbc.dialect.R2dbcDialect
import org.springframework.r2dbc.core.DatabaseClient

internal class DefaultSpringR2dbcKueryClientBuilder : SpringR2dbcKueryClientBuilder {
    private var connectionFactory: ConnectionFactory? = null
    private var converters: List<Any> = emptyList()
    private var databaseClient: DatabaseClient? = null
    private var conversionService: ConversionService? = null

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

    override fun conversionService(conversionService: ConversionService): SpringR2dbcKueryClientBuilder {
        this.conversionService = conversionService
        return this
    }

    override fun build(): KueryClient {
        val databaseClient = this.databaseClient ?: defaultDatabaseClient()
        val conversionService = this.conversionService ?: defaultConversionService()
        return DefaultSpringR2dbcKueryClient(databaseClient, conversionService)
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

    private fun defaultConversionService(): ConversionService {
        val conversionService = DefaultConversionService()
        val customConversions = r2dbcCustomConversions()
        customConversions.registerConvertersIn(conversionService)
        return conversionService
    }

    // ref: https://github.com/spring-projects/spring-data-relational/blob/abd0c85629756d34b98ca13b2a3eff341b832d25/spring-data-r2dbc/src/main/java/org/springframework/data/r2dbc/config/AbstractR2dbcConfiguration.java#L236-L238
    private fun r2dbcCustomConversions(): R2dbcCustomConversions {
        return R2dbcCustomConversions(storeConversions(), converters)
    }

    // ref: https://github.com/spring-projects/spring-data-relational/blob/abd0c85629756d34b98ca13b2a3eff341b832d25/spring-data-r2dbc/src/main/java/org/springframework/data/r2dbc/config/AbstractR2dbcConfiguration.java#L254-L262
    private fun storeConversions(): StoreConversions {
        val connectionFactory = requireNotNull(databaseClient?.connectionFactory ?: connectionFactory) {
            "Specify either databaseClient or connectionFactory."
        }
        val dialect: R2dbcDialect = DialectResolver.getDialect(connectionFactory)
        val converters = buildList {
            addAll(dialect.converters)
            addAll(R2dbcCustomConversions.STORE_CONVERTERS)
        }
        return StoreConversions.of(dialect.simpleTypeHolder, converters)
    }
}
