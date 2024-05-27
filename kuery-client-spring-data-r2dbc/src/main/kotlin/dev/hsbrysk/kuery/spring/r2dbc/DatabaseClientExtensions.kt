package dev.hsbrysk.kuery.spring.r2dbc

import dev.hsbrysk.kuery.core.Sql
import dev.hsbrysk.kuery.core.SqlDsl
import org.springframework.r2dbc.core.DatabaseClient

fun DatabaseClient.sql(block: SqlDsl.() -> Unit): DatabaseClient.GenericExecuteSpec {
    val sql = Sql.create(block)
    @Suppress("SqlSourceToSinkFlow")
    return sql.parameters.fold(this.sql(sql.body)) { acc, parameter ->
        if (parameter.value != null) {
            acc.bindNull(parameter.name, parameter.kClass.java)
        } else {
            acc.bind(parameter.name, checkNotNull(parameter.value))
        }
    }
}
