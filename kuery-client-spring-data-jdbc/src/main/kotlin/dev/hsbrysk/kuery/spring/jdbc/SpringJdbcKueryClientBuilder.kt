package dev.hsbrysk.kuery.spring.jdbc

import dev.hsbrysk.kuery.core.KueryBlockingClient
import javax.sql.DataSource

interface SpringJdbcKueryClientBuilder {
    fun dataSource(dataSource: DataSource): SpringJdbcKueryClientBuilder

    fun converters(converters: List<Any>): SpringJdbcKueryClientBuilder

    fun build(): KueryBlockingClient
}
