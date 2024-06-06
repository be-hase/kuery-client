package dev.hsbrysk.kuery.core

import dev.hsbrysk.kuery.core.internal.DefaultNamedSqlParameter
import kotlin.reflect.KClass

interface NamedSqlParameter<T : Any> {
    /**
     * parameter name
     */
    val name: String

    /**
     * value
     */
    val value: T?

    /**
     * [KClass] of value
     */
    val kClass: KClass<T>

    companion object {
        fun <T : Any> of(
            name: String,
            value: T?,
            kClass: KClass<T>,
        ): NamedSqlParameter<T> {
            return DefaultNamedSqlParameter(name, value, kClass)
        }

        inline fun <reified T : Any> of(
            name: String,
            value: T?,
        ): NamedSqlParameter<T> {
            return of(name, value, T::class)
        }
    }
}
