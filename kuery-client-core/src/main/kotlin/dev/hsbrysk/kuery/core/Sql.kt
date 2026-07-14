package dev.hsbrysk.kuery.core

import dev.hsbrysk.kuery.core.internal.DefaultSql
import dev.hsbrysk.kuery.core.internal.DefaultSqlBuilder

interface Sql {
    /**
     * SQL body
     */
    val body: String

    /**
     * SQL parameters
     */
    val parameters: List<NamedSqlParameter>

    companion object
}

/**
 * Create [Sql]
 */
fun Sql(
    body: String,
    parameters: List<NamedSqlParameter> = emptyList(),
): Sql = DefaultSql(body, parameters.toList())

/**
 * Create [Sql] using [SqlBuilder]
 */
fun Sql(block: SqlBuilder.() -> Unit): Sql {
    val builder = DefaultSqlBuilder()
    block(builder)
    return builder.build()
}
