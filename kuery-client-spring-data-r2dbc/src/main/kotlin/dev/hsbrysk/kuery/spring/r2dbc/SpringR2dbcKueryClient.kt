package dev.hsbrysk.kuery.spring.r2dbc

import dev.hsbrysk.kuery.spring.r2dbc.internal.DefaultSpringR2dbcKueryClientBuilder
import reactor.util.context.ContextView

object SpringR2dbcKueryClient {
    val SQL_ID_REACTOR_CONTEXT_KEY = "${SpringR2dbcKueryClient::class.java.name}:sqlId"

    /**
     * Retrieve the current sqlId from reactor context.
     */
    fun sqlId(context: ContextView): String? {
        return context.getOrEmpty<String>(SQL_ID_REACTOR_CONTEXT_KEY).orElse(null)
    }

    /**
     * Create [SpringR2dbcKueryClientBuilder]
     */
    fun builder(): SpringR2dbcKueryClientBuilder {
        return DefaultSpringR2dbcKueryClientBuilder()
    }
}
