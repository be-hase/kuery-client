package dev.hsbrysk.kuery.core

import org.intellij.lang.annotations.Language

/**
 * DSL scope for building SQL.
 *
 * Add SQL fragments with [add] or [String.unaryPlus]; the fragments are joined with line breaks
 * to form the final statement. Thanks to the Kuery Client compiler plugin, any value interpolated
 * into those fragments (`$value`) is bound as a named parameter instead of being embedded in the
 * SQL text, which prevents SQL injection.
 *
 * ```kotlin
 * client.sql {
 *     +"SELECT * FROM users"
 *     +"WHERE user_id = $userId"
 * }
 * ```
 *
 * For dynamic SQL that cannot go through the compiler plugin (e.g. helper extension functions
 * that assemble fragments programmatically), use [addUnsafe] together with [bind].
 *
 * This interface is sealed because the compiler plugin assumes the receiver is the library's
 * internal implementation; a user implementation (e.g. a test fake) would fail with
 * [ClassCastException] at runtime.
 */
@SqlBuilderMarker
public sealed interface SqlBuilder {
    /**
     * Adds a SQL fragment to the statement being built.
     *
     * Due to the Kotlin compiler plugin, every value interpolated into [sql] is expanded into a
     * named placeholder and bound as a parameter.
     *
     * ```kotlin
     * add("SELECT * FROM users WHERE user_id = $userId")
     * ```
     */
    public fun add(@Language("sql") sql: String)

    /**
     * Adds a SQL fragment to the statement being built. Operator shorthand for [add].
     *
     * Due to the Kotlin compiler plugin, every value interpolated into the receiver string is
     * expanded into a named placeholder and bound as a parameter.
     *
     * ```kotlin
     * +"SELECT * FROM users WHERE user_id = $userId"
     * ```
     */
    public operator fun @receiver:Language("sql") String.unaryPlus()

    /**
     * Adds a SQL fragment to the statement being built, WITHOUT rewriting string interpolation
     * into bind parameters.
     *
     * Any value interpolated into [sql] is embedded in the SQL text as-is, so passing
     * user-controlled input directly can lead to SQL injection. To include dynamic values
     * safely, bind them with [bind]:
     *
     * ```kotlin
     * addUnsafe("user_id = ${bind(userId)}")
     * ```
     */
    @DelicateKueryClientApi
    public fun addUnsafe(@Language("sql") sql: String)

    /**
     * Binds [parameter] as a named parameter and returns the placeholder string (e.g. `:p0`)
     * to embed in the SQL fragment.
     *
     * It is intended to be used together with [addUnsafe]; fragments passed to [add] /
     * [String.unaryPlus] bind interpolated values automatically.
     */
    @DelicateKueryClientApi
    public fun bind(parameter: Any?): String
}
