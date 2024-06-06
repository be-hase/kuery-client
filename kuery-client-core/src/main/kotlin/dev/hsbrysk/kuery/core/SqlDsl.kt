package dev.hsbrysk.kuery.core

import org.intellij.lang.annotations.Language

@SqlDslMarker
interface SqlDsl {
    /**
     * Enter the sql you want to execute. Appended to the internally held StringBuilder.
     */
    fun add(@Language("sql") sql: String)

    /**
     * Enter the sql you want to execute. Appended to the internally held StringBuilder.
     */
    operator fun String.unaryPlus()

    /**
     * Bind variables to SQL
     */
    fun <T : Any> bind(value: T?): String
}

private val NUMBER_REGEX = "^[0-9]+$".toRegex()

// need cache?
fun (SqlDsl.() -> Unit).id(): String {
    val parts = this.javaClass.name.split("$").filterNot { it.matches(NUMBER_REGEX) }
    return if (parts.isEmpty()) {
        "UNKNOWN"
    } else {
        parts.joinToString(".")
    }
}
