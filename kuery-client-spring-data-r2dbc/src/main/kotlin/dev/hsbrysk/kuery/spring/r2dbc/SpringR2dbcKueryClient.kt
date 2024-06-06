package dev.hsbrysk.kuery.spring.r2dbc

import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.spring.r2dbc.internal.DefaultSpringR2dbcKueryClientBuilder

object SpringR2dbcKueryClient {
    val SQL_ID_REACTOR_CONTEXT_KEY = "${KueryClient::class.java.name}:sqlId"

    fun builder(): SpringR2dbcKueryClientBuilder {
        return DefaultSpringR2dbcKueryClientBuilder()
    }
}
