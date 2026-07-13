package dev.hsbrysk.kuery.spring.jdbc

import dev.hsbrysk.kuery.spring.jdbc.internal.DefaultSpringJdbcKueryClientBuilder

public object SpringJdbcKueryClient {
    /**
     * Retrieve the running sqlId from thread local.
     */
    public fun sqlId(): String? = sqlIdThreadLocal.get()

    /**
     * Create [SpringJdbcKueryClientBuilder]
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
