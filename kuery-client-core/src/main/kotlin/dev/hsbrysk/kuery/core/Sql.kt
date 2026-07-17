package dev.hsbrysk.kuery.core

import dev.hsbrysk.kuery.core.internal.DefaultSql

/**
 * An immutable SQL statement built by [SqlBuilder]: the SQL text with named placeholders and
 * the parameters bound to them.
 */
public interface Sql {
    /**
     * The SQL text, containing named placeholders (e.g. `:p0`) instead of the interpolated
     * values.
     */
    public val body: String

    /**
     * The parameters bound to the placeholders in [body].
     */
    public val parameters: List<NamedSqlParameter>

    public companion object
}

/**
 * Creates a [Sql] from the given [body] and [parameters].
 */
public fun Sql(
    body: String,
    parameters: List<NamedSqlParameter> = emptyList(),
): Sql = DefaultSql(body, parameters.toList())

/**
 * Creates a [Sql] by running the given [SqlBuilder] block.
 *
 * ```kotlin
 * val sql = Sql { +"SELECT * FROM users WHERE user_id = $userId" }
 * ```
 */
public fun Sql(block: SqlBuilder.() -> Unit): Sql {
    val builder = DefaultSqlBuilder()
    block(builder)
    return builder.build()
}
