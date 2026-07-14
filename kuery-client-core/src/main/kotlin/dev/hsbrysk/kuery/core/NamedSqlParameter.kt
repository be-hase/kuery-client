package dev.hsbrysk.kuery.core

import dev.hsbrysk.kuery.core.internal.DefaultNamedSqlParameter

public interface NamedSqlParameter {
    /**
     * parameter name
     */
    public val name: String

    /**
     * value
     */
    public val value: Any?

    public companion object
}

/**
 * Create [NamedSqlParameter]
 */
public fun NamedSqlParameter(
    name: String,
    value: Any?,
): NamedSqlParameter = DefaultNamedSqlParameter(name, value)
