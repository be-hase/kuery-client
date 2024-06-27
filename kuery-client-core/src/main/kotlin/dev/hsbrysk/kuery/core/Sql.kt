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
        fun of(
            body: String,
            parameters: List<NamedSqlParameter>,
        ): Sql = DefaultSql(body, parameters)

        /**
         * Create [Sql] using [SqlBuilder]
         */
        fun create(block: SqlBuilder.() -> Unit): Sql {
            val builder = DefaultSqlBuilder()
            block(builder)
            return builder.build()
        }
    }
}
