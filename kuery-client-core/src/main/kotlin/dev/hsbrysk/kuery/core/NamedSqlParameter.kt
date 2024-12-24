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
        /**
         * Create [NamedSqlParameter]
         */
        @Deprecated(
            message = "Use `dev.hsbrysk.kuery.core.NamedSqlParameter` function instead.",
            replaceWith = ReplaceWith("NamedSqlParameter(name, value)"),
        )
        fun of(
            name: String,
            value: Any?,
        ): NamedSqlParameter = DefaultNamedSqlParameter(name, value)
    }
}

/**
 * Create [NamedSqlParameter]
 */
fun NamedSqlParameter(
    name: String,
    value: Any?,
): NamedSqlParameter = DefaultNamedSqlParameter(name, value)
