package dev.hsbrysk.kuery.spring.jdbc

import dev.hsbrysk.kuery.spring.jdbc.internal.DefaultSpringJdbcKueryClientBuilder

object SpringJdbcKueryClient {
    fun sqlId(): String? {
        return sqlIdThreadLocal.get()
    }

    fun builder(): SpringJdbcKueryClientBuilder {
        return DefaultSpringJdbcKueryClientBuilder()
    }
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
