package dev.hsbrysk.kuery.core

import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

interface FetchSpec {
    suspend fun single(): Map<String, Any?>

    suspend fun <T : Any> single(returnType: KClass<T>): T

    suspend fun singleOrNull(): Map<String, Any?>?

    suspend fun <T : Any> singleOrNull(returnType: KClass<T>): T?

    suspend fun list(): List<Map<String, Any?>>

    suspend fun <T : Any> list(returnType: KClass<T>): List<T>

    fun flow(): Flow<Map<String, Any?>>

    fun <T : Any> flow(returnType: KClass<T>): Flow<T>

    suspend fun rowsUpdated(): Long

    suspend fun generatedValues(vararg columns: String): Map<String, Any>
}
