package dev.hsbrysk.kuery.core

import org.intellij.lang.annotations.Language

/**
 * DSL scope for building SQL.
 *
 * This interface is sealed because the compiler plugin assumes the receiver is the library's
 * internal implementation; a user implementation (e.g. a test fake) would fail with
 * [ClassCastException] at runtime.
 */
@SqlBuilderMarker
public sealed interface SqlBuilder {
    /**
     * Specify the sql you want to execute. Appended to the internally held [StringBuilder].
     * Due to the Kotlin compiler plugin, the string interpolation within the string template passed to
     * this method will be expanded using placeholders.
     *
     * e.g.
     * ```
     * add("SELECT * FROM users WHERE user_id = $userId")
     * ```
     */
    public fun add(@Language("sql") sql: String)

    /**
     * Specify the sql you want to execute. Appended to the internally held [StringBuilder].
     * Due to the Kotlin compiler plugin, the string interpolation within the string template passed to
     * this method will be expanded using placeholders.
     *
     * e.g.
     * ```
     * +"SELECT * FROM users WHERE user_id = $userId"
     * ```
     */
    public operator fun String.unaryPlus()

    /**
     * Specify the sql you want to execute. Appended to the internally held [StringBuilder].
     * Please note that string interpolation using placeholders will not be performed in this method.
     *
     * If you want to insert dynamic values using addUnsafe, please use bind.
     * ```
     * addUnsafe("user_id = ${bind(userId)}")
     * ```
     */
    @DelicateKueryClientApi
    public fun addUnsafe(@Language("sql") sql: String)

    /**
     * Bind variables to SQL
     * It is intended to be used together with addUnsafe.
     */
    @DelicateKueryClientApi
    public fun bind(parameter: Any?): String
}
