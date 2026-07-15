package dev.hsbrysk.kuery.spring.r2dbc

import dev.hsbrysk.kuery.spring.r2dbc.internal.DefaultSpringR2dbcKueryClientBuilder
import reactor.util.context.ContextView

public object SpringR2dbcKueryClient {
    public val SQL_ID_REACTOR_CONTEXT_KEY: String = "${SpringR2dbcKueryClient::class.java.name}:sqlId"

    /**
     * Retrieve the current sqlId from reactor context.
     */
    public fun sqlId(context: ContextView): String? = context.getOrDefault(SQL_ID_REACTOR_CONTEXT_KEY, null)

    /**
     * Create [SpringR2dbcKueryClientBuilder]
     */
    public fun builder(): SpringR2dbcKueryClientBuilder = DefaultSpringR2dbcKueryClientBuilder()
}
