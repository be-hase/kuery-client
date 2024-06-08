package dev.hsbrysk.kuery.core

import dev.hsbrysk.kuery.core.KueryBlockingClient.FetchSpec
import kotlin.reflect.KClass

interface KueryBlockingClient {
    fun sql(block: SqlDsl.() -> Unit): FetchSpec

    interface FetchSpec {
        /**
         * Receives the results as a map.
         */
        fun singleMap(): Map<String, Any?>

        /**
         * Receives the results as a map.
         */
        fun singleMapOrNull(): Map<String, Any?>?

        /**
         * Receives the results converted to the specified type.
         */
        fun <T : Any> single(returnType: KClass<T>): T

        /**
         * Receives the results converted to the specified type.
         */
        fun <T : Any> singleOrNull(returnType: KClass<T>): T?

        /**
         * Receives the results of multiple rows as a map.
         */
        fun listMap(): List<Map<String, Any?>>

        /**
         * Receives the results of multiple rows converted to the specified type.
         */
        fun <T : Any> list(returnType: KClass<T>): List<T>

        /**
         * Contract for fetching the number of affected rows
         */
        fun rowsUpdated(): Long

        /**
         * Receives the values generated on the database side.
         * For example, an auto increment value.
         */
        fun generatedValues(vararg columns: String): Map<String, Any>
    }
}

/**
 * Receives the results converted to the specified type.
 */
inline fun <reified T : Any> FetchSpec.single(): T {
    return single(T::class)
}

/**
 * Receives the results converted to the specified type.
 */
inline fun <reified T : Any> FetchSpec.singleOrNull(): T? {
    return singleOrNull(T::class)
}

/**
 * Receives the results of multiple rows converted to the specified type.
 */
inline fun <reified T : Any> FetchSpec.list(): List<T> {
    return list(T::class)
}
