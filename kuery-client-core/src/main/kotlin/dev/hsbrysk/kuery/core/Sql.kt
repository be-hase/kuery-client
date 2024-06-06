package dev.hsbrysk.kuery.core

import dev.hsbrysk.kuery.core.internal.DefaultSql
import dev.hsbrysk.kuery.core.internal.SqlBuilder

interface Sql {
    /**
     * SQL body
     */
    val body: String

    /**
     * SQL parameters
     */
    val parameters: List<NamedSqlParameter<*>>

    companion object {
        /**
         * Create Sql
         */
        fun of(
            body: String,
            parameters: List<NamedSqlParameter<*>>,
        ): Sql {
            return DefaultSql(body, parameters)
        }

        /**
         * Create Sql using DSL
         */
        fun create(block: SqlDsl.() -> Unit): Sql {
            val builder = SqlBuilder()
            block(builder)
            return builder.build()
        }
    }
}
