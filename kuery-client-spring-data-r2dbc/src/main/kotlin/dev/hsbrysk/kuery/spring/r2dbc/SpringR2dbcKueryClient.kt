package dev.hsbrysk.kuery.spring.r2dbc

import dev.hsbrysk.kuery.spring.r2dbc.internal.DefaultSpringR2dbcKueryClientBuilder
import reactor.util.context.ContextView

/**
 * Entry point for creating a [dev.hsbrysk.kuery.core.KueryClient] backed by Spring Data R2DBC.
 *
 * ```kotlin
 * val client = SpringR2dbcKueryClient.builder()
 *     .connectionFactory(connectionFactory)
 *     .build()
 * ```
 */
public object SpringR2dbcKueryClient {
    /**
     * The Reactor context key under which the sqlId of the currently executing query is stored.
     *
     * Usually you don't need this key directly; use [sqlId] instead.
     */
    public val SQL_ID_REACTOR_CONTEXT_KEY: String = "${SpringR2dbcKueryClient::class.java.name}:sqlId"

    /**
     * Retrieves the sqlId of the currently executing query from the given Reactor context.
     *
     * This is useful for referring to the sqlId in downstream instrumentation, such as an
     * r2dbc-proxy listener.
     *
     * @return the sqlId, or `null` if no query is executing in this context
     */
    public fun sqlId(context: ContextView): String? = context.getOrDefault(SQL_ID_REACTOR_CONTEXT_KEY, null)

    /**
     * Creates a [SpringR2dbcKueryClientBuilder].
     */
    public fun builder(): SpringR2dbcKueryClientBuilder = DefaultSpringR2dbcKueryClientBuilder()
}
