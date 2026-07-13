package dev.hsbrysk.kuery.core

import dev.hsbrysk.kuery.core.internal.DefaultSql
import dev.hsbrysk.kuery.core.internal.DefaultSqlBuilder

public interface Sql {
    /**
     * SQL body
     */
    public val body: String

    /**
     * SQL parameters
     */
    public val parameters: List<NamedSqlParameter>

    public companion object
}

/**
 * Create [Sql]
 */
public fun Sql(
    body: String,
    parameters: List<NamedSqlParameter> = emptyList(),
): Sql = DefaultSql(body, parameters.toList())

/**
 * Create [Sql] using [SqlBuilder]
 */
public fun Sql(block: SqlBuilder.() -> Unit): Sql {
    val builder = DefaultSqlBuilder()
    block(builder)
    return builder.build()
}
