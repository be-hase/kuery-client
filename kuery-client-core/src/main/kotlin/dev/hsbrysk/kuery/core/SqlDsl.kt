package dev.hsbrysk.kuery.core

import org.intellij.lang.annotations.Language
import kotlin.reflect.KClass

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
    fun <T : Any> bind(
        value: T?,
        kClass: KClass<T>,
    ): String
}

inline fun <reified T : Any> SqlDsl.bind(value: T?): String {
    return bind(value, T::class)
}

private val NUMBER_REGEX = "^[0-9]+$".toRegex()

fun (SqlDsl.() -> Unit).id(): String {
    val parts = this.javaClass.name.split("$").filterNot { it.matches(NUMBER_REGEX) }
    return if (parts.isEmpty()) {
        "UNKNOWN"
    } else {
        parts.joinToString(".")
    }
}
