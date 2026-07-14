package dev.hsbrysk.kuery.core

import org.intellij.lang.annotations.Language

/**
 * DSL scope for building SQL.
 *
 * This interface is not intended to be implemented by users (e.g. as a test fake).
 * The compiler plugin assumes the receiver is the library's internal implementation:
 * a user implementation fails with [ClassCastException] at runtime, and if the static type
 * of the receiver is a subtype of [SqlBuilder], the rewrite is skipped entirely and the raw
 * interpolated string is passed to [add] — losing the parameter-binding guarantee this
 * library exists to provide.
 */
@SqlBuilderMarker
interface SqlBuilder {
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
    fun add(@Language("sql") sql: String)

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
    operator fun String.unaryPlus()

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
    fun addUnsafe(@Language("sql") sql: String)

    /**
     * Bind variables to SQL
     * It is intended to be used together with addUnsafe.
     */
    @DelicateKueryClientApi
    fun bind(parameter: Any?): String
}
