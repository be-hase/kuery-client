package dev.hsbrysk.kuery.spring.jdbc

import dev.hsbrysk.kuery.spring.jdbc.internal.DefaultSpringJdbcKueryClientBuilder

object SpringJdbcKueryClient {
    fun sqlId(): String? {
        TODO()
    }

    fun builder(): SpringJdbcKueryClientBuilder {
        return DefaultSpringJdbcKueryClientBuilder()
    }
}
