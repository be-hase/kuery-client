package dev.hsbrysk.kuery.core

import dev.hsbrysk.kuery.core.KueryBlockingClient.FetchSpec
import kotlin.reflect.KClass

interface KueryBlockingClient {
    fun sql(block: SqlDsl.() -> Unit): FetchSpec

    interface FetchSpec {
        fun singleMap(): Map<String, Any?>

        fun <T : Any> single(returnType: KClass<T>): T

        fun singleMapOrNull(): Map<String, Any?>?

        fun <T : Any> singleOrNull(returnType: KClass<T>): T?

        fun listMap(): List<Map<String, Any?>>

        fun <T : Any> list(returnType: KClass<T>): List<T>

        fun rowsUpdated(): Long

        fun generatedValues(vararg columns: String): Map<String, Any>
    }
}

inline fun <reified T : Any> FetchSpec.single(): T {
    return single(T::class)
}

inline fun <reified T : Any> FetchSpec.singleOrNull(): T? {
    return singleOrNull(T::class)
}

inline fun <reified T : Any> FetchSpec.list(): List<T> {
    return list(T::class)
}
