package dev.hsbrysk.kuery.core

import dev.hsbrysk.kuery.core.KueryClient.FetchSpec
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

interface KueryClient {
    /**
     *
     */
    fun sql(block: SqlDsl.() -> Unit): FetchSpec

    interface FetchSpec {
        suspend fun singleMap(): Map<String, Any?>

        suspend fun <T : Any> single(returnType: KClass<T>): T

        suspend fun singleMapOrNull(): Map<String, Any?>?

        suspend fun <T : Any> singleOrNull(returnType: KClass<T>): T?

        suspend fun listMap(): List<Map<String, Any?>>

        suspend fun <T : Any> list(returnType: KClass<T>): List<T>

        fun flowMap(): Flow<Map<String, Any?>>

        fun <T : Any> flow(returnType: KClass<T>): Flow<T>

        suspend fun rowsUpdated(): Long

        suspend fun generatedValues(vararg columns: String): Map<String, Any>
    }
}

suspend inline fun <reified T : Any> FetchSpec.single(): T {
    return single(T::class)
}

suspend inline fun <reified T : Any> FetchSpec.singleOrNull(): T? {
    return singleOrNull(T::class)
}

suspend inline fun <reified T : Any> FetchSpec.list(): List<T> {
    return list(T::class)
}

inline fun <reified T : Any> FetchSpec.flow(): Flow<T> {
    return flow(T::class)
}
