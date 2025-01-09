package dev.hsbrysk.kuery.core

import org.intellij.lang.annotations.Language

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
