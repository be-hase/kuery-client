package dev.hsbrysk.kuery.core

import dev.hsbrysk.kuery.core.internal.DefaultNamedSqlParameter

interface NamedSqlParameter {
    /**
     * parameter name
     */
    val name: String

    /**
     * value
     */
    val value: Any?

    companion object {
        fun of(
            name: String,
            value: Any?,
        ): NamedSqlParameter {
            return DefaultNamedSqlParameter(name, value)
        }
    }
}
