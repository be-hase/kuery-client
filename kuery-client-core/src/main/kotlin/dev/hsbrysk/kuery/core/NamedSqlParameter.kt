package dev.hsbrysk.kuery.core

import dev.hsbrysk.kuery.core.internal.DefaultNamedSqlParameter

/**
 * A named SQL bind parameter: the pair of a placeholder name in [Sql.body] and the value bound
 * to it.
 */
public interface NamedSqlParameter {
    /**
     * The parameter name, without the leading colon (e.g. `p0` for the placeholder `:p0`).
     */
    public val name: String

    /**
     * The value bound to the parameter. `null` is bound as SQL NULL.
     */
    public val value: Any?

    public companion object
}

/**
 * Creates a [NamedSqlParameter] with the given [name] and [value].
 */
public fun NamedSqlParameter(
    name: String,
    value: Any?,
): NamedSqlParameter = DefaultNamedSqlParameter(name, value)
