package dev.hsbrysk.kuery.core

import dev.hsbrysk.kuery.core.internal.DefaultSql
import dev.hsbrysk.kuery.core.internal.SqlBuilder

interface Sql {
    val body: String
    val parameters: List<NamedSqlParameter<*>>

    companion object {
        fun of(
            body: String,
            parameters: List<NamedSqlParameter<*>>,
        ): Sql {
            return DefaultSql(body, parameters)
        }

        fun create(block: SqlDsl.() -> Unit): Sql {
            val builder = SqlBuilder()
            block(builder)
            return builder.build()
        }
    }
}
