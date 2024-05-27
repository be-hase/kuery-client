package dev.hsbrysk.kuery.spring.r2dbc

import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.core.KueryFetchSpec
import dev.hsbrysk.kuery.spring.r2dbc.internal.DefaultSpringR2dbcKueryClient
import org.springframework.core.convert.ConversionService
import org.springframework.core.convert.support.DefaultConversionService
import org.springframework.r2dbc.core.DatabaseClient

interface SpringR2dbcKueryClient : KueryClient {
    companion object {
        val SQL_ID_CONTEXT_KEY = "${KueryClient::class.java.name}:sqlId"

        fun of(databaseClient: DatabaseClient): SpringR2dbcKueryClient {
            return of(databaseClient, DefaultConversionService.getSharedInstance())
        }

        fun of(
            databaseClient: DatabaseClient,
            conversionService: ConversionService,
        ): SpringR2dbcKueryClient {
            return DefaultSpringR2dbcKueryClient(databaseClient, conversionService)
        }
    }
}

interface SpringR2dbcKueryFetchSpec : KueryFetchSpec
