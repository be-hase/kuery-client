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

    companion object {
        /**
         * Create [Sql]
         */
        @Deprecated(
            message = "Use `dev.hsbrysk.kuery.core.Sql` function instead.",
            replaceWith = ReplaceWith("Sql(body, parameters)"),
        )
        fun of(
            body: String,
            parameters: List<NamedSqlParameter>,
        ): Sql = DefaultSql(body, parameters)

        /**
         * Create [Sql] using [SqlBuilder]
         */
        @Deprecated(
            message = "Use `dev.hsbrysk.kuery.core.Sql` function instead.",
            replaceWith = ReplaceWith("Sql(block)"),
        )
        fun create(block: SqlBuilder.() -> Unit): Sql {
            val builder = DefaultSqlBuilder()
            block(builder)
            return builder.build()
        }
    }
}

/**
 * Create [Sql]
 */
fun Sql(
    body: String,
    parameters: List<NamedSqlParameter> = emptyList(),
): Sql = DefaultSql(body, parameters)

/**
 * Create [Sql] using [SqlBuilder]
 */
fun Sql(block: SqlBuilder.() -> Unit): Sql {
    val builder = DefaultSqlBuilder()
    block(builder)
    return builder.build()
}
