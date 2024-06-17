package dev.hsbrysk.kuery.core

import org.intellij.lang.annotations.Language

@SqlBuilderMarker
interface SqlBuilder {
    /**
     * Specify the sql you want to execute. Appended to the internally held [StringBuilder].
     */
    fun add(@Language("sql") sql: String)

    /**
     * Specify the sql you want to execute. Appended to the internally held [StringBuilder].
     */
    operator fun String.unaryPlus()

    /**
     * Bind variables to SQL
     */
    fun <T : Any> bind(value: T?): String
}
