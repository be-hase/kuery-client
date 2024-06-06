package dev.hsbrysk.kuery.core

import dev.hsbrysk.kuery.core.internal.DefaultNamedSqlParameter

interface NamedSqlParameter<T : Any> {
    /**
     * parameter name
     */
    val name: String

    /**
     * value
     */
    val value: T?

    companion object {
        fun <T : Any> of(
            name: String,
            value: T?,
        ): NamedSqlParameter<T> {
            return DefaultNamedSqlParameter(name, value)
        }
    }
}
