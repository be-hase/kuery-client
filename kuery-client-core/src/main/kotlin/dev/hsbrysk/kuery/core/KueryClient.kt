package dev.hsbrysk.kuery.core

import dev.hsbrysk.kuery.core.KueryClient.FetchSpec
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

interface KueryClient {
    /**
     * Receives a DSL for constructing SQL and returns a FetchSpec that receives the execution results.
     */
    fun sql(block: SqlDsl.() -> Unit): FetchSpec

    interface FetchSpec {
        /**
         * Receives the results as a map.
         */
        suspend fun singleMap(): Map<String, Any?>

        /**
         * Receives the results as a map.
         */
        suspend fun singleMapOrNull(): Map<String, Any?>?

        /**
         * Receives the results converted to the specified type.
         */
        suspend fun <T : Any> single(returnType: KClass<T>): T

        /**
         * Receives the results converted to the specified type.
         */
        suspend fun <T : Any> singleOrNull(returnType: KClass<T>): T?

        /**
         * Receives the results of multiple rows as a map.
         */
        suspend fun listMap(): List<Map<String, Any?>>

        /**
         * Receives the results of multiple rows converted to the specified type.
         */
        suspend fun <T : Any> list(returnType: KClass<T>): List<T>

        /**
         * Receives the results of multiple rows as a map.
         */
        fun flowMap(): Flow<Map<String, Any?>>

        /**
         * Receives the results of multiple rows converted to the specified type.
         */
        fun <T : Any> flow(returnType: KClass<T>): Flow<T>

        /**
         * Contract for fetching the number of affected rows
         */
        suspend fun rowsUpdated(): Long

        /**
         * Receives the values generated on the database side.
         * For example, an auto increment value.
         */
        suspend fun generatedValues(vararg columns: String): Map<String, Any>
    }
}

/**
 * Receives the results converted to the specified type.
 */
suspend inline fun <reified T : Any> FetchSpec.single(): T {
    return single(T::class)
}

/**
 * Receives the results converted to the specified type.
 */
suspend inline fun <reified T : Any> FetchSpec.singleOrNull(): T? {
    return singleOrNull(T::class)
}

/**
 * Receives the results of multiple rows converted to the specified type.
 */
suspend inline fun <reified T : Any> FetchSpec.list(): List<T> {
    return list(T::class)
}

/**
 * Receives the results of multiple rows converted to the specified type.
 */
inline fun <reified T : Any> FetchSpec.flow(): Flow<T> {
    return flow(T::class)
}
