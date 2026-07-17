package dev.hsbrysk.kuery.spring.jdbc

import dev.hsbrysk.kuery.spring.jdbc.internal.DefaultSpringJdbcKueryClientBuilder

/**
 * Entry point for creating a [dev.hsbrysk.kuery.core.KueryBlockingClient] backed by
 * Spring Data JDBC.
 *
 * ```kotlin
 * val client = SpringJdbcKueryClient.builder()
 *     .dataSource(dataSource)
 *     .build()
 * ```
 */
public object SpringJdbcKueryClient {
    /**
     * Retrieves the sqlId of the query currently executing on this thread.
     *
     * This is useful for referring to the sqlId in downstream instrumentation, such as a
     * datasource-proxy listener.
     *
     * @return the sqlId, or `null` if no query is executing on this thread
     */
    public fun sqlId(): String? = sqlIdThreadLocal.get()

    /**
     * Creates a [SpringJdbcKueryClientBuilder].
     */
    public fun builder(): SpringJdbcKueryClientBuilder = DefaultSpringJdbcKueryClientBuilder()
}

private val sqlIdThreadLocal = ThreadLocal<String>()

internal class SqlIdInjector(sqlId: String) : AutoCloseable {
    private val old: String? = sqlIdThreadLocal.get()

    init {
        sqlIdThreadLocal.set(sqlId)
    }

    override fun close() {
        sqlIdThreadLocal.set(old)
    }
}
