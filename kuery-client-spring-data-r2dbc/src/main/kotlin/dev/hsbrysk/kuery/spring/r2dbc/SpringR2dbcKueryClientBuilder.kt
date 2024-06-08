package dev.hsbrysk.kuery.spring.r2dbc

import dev.hsbrysk.kuery.core.KueryClient
import io.r2dbc.spi.ConnectionFactory

interface SpringR2dbcKueryClientBuilder {
    fun connectionFactory(connectionFactory: ConnectionFactory): SpringR2dbcKueryClientBuilder

    fun converters(converters: List<Any>): SpringR2dbcKueryClientBuilder

    fun build(): KueryClient
}
