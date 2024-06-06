package dev.hsbrysk.kuery.core

import kotlin.reflect.KClass

interface KueryBlockingClient {
    fun sql(block: SqlDsl.() -> Unit): FetchSpec

    interface FetchSpec {
        fun single(): Map<String, Any?>

        fun <T : Any> single(returnType: KClass<T>): T

        fun singleOrNull(): Map<String, Any?>?

        fun <T : Any> singleOrNull(returnType: KClass<T>): T?

        fun list(): List<Map<String, Any?>>

        fun <T : Any> list(returnType: KClass<T>): List<T>

        fun rowsUpdated(): Long

        fun generatedValues(vararg columns: String): Map<String, Any>
    }
}
