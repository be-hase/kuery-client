package com.example.compiler

import dev.hsbrysk.kuery.core.CloseableSequence
import dev.hsbrysk.kuery.core.KueryBlockingClient
import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.core.KueryClientInternalApi
import dev.hsbrysk.kuery.core.SqlBuilder
import dev.hsbrysk.kuery.core.internal.SqlIds.id
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * Records the sqlId of every `sql` call so that compiled snippets can expose the ids the
 * compiler plugin injected. The terminal operations are irrelevant to those tests and simply
 * fail.
 */
@OptIn(KueryClientInternalApi::class)
class RecordingKueryClient : KueryClient {
    val sqlIds = mutableListOf<String>()

    override fun sql(
        sqlId: String,
        block: SqlBuilder.() -> Unit,
    ): KueryClient.FetchSpec {
        sqlIds += sqlId
        return UnsupportedFetchSpec
    }

    override fun sql(block: SqlBuilder.() -> Unit): KueryClient.FetchSpec = sql(block.id(), block)

    private object UnsupportedFetchSpec : KueryClient.FetchSpec {
        override fun fetchSize(fetchSize: Int): KueryClient.FetchSpec = unsupported()

        override suspend fun singleMap(): Map<String, Any?> = unsupported()

        override suspend fun singleMapOrNull(): Map<String, Any?>? = unsupported()

        override suspend fun <T : Any> single(returnType: KClass<T>): T = unsupported()

        override suspend fun <T : Any> singleOrNull(returnType: KClass<T>): T? = unsupported()

        override suspend fun listMap(): List<Map<String, Any?>> = unsupported()

        override suspend fun <T : Any> list(returnType: KClass<T>): List<T> = unsupported()

        override fun flowMap(): Flow<Map<String, Any?>> = unsupported()

        override fun <T : Any> flow(returnType: KClass<T>): Flow<T> = unsupported()

        override suspend fun rowsUpdated(): Long = unsupported()

        override suspend fun generatedValues(vararg columns: String): Map<String, Any> = unsupported()
    }
}

/**
 * The blocking counterpart of [RecordingKueryClient].
 */
@OptIn(KueryClientInternalApi::class)
class RecordingKueryBlockingClient : KueryBlockingClient {
    val sqlIds = mutableListOf<String>()

    override fun sql(
        sqlId: String,
        block: SqlBuilder.() -> Unit,
    ): KueryBlockingClient.FetchSpec {
        sqlIds += sqlId
        return UnsupportedFetchSpec
    }

    override fun sql(block: SqlBuilder.() -> Unit): KueryBlockingClient.FetchSpec = sql(block.id(), block)

    private object UnsupportedFetchSpec : KueryBlockingClient.FetchSpec {
        override fun fetchSize(fetchSize: Int): KueryBlockingClient.FetchSpec = unsupported()

        override fun maxRows(maxRows: Int): KueryBlockingClient.FetchSpec = unsupported()

        override fun queryTimeoutSeconds(queryTimeoutSeconds: Int): KueryBlockingClient.FetchSpec = unsupported()

        override fun singleMap(): Map<String, Any?> = unsupported()

        override fun singleMapOrNull(): Map<String, Any?>? = unsupported()

        override fun <T : Any> single(returnType: KClass<T>): T = unsupported()

        override fun <T : Any> singleOrNull(returnType: KClass<T>): T? = unsupported()

        override fun listMap(): List<Map<String, Any?>> = unsupported()

        override fun <T : Any> list(returnType: KClass<T>): List<T> = unsupported()

        override fun sequenceMap(): CloseableSequence<Map<String, Any?>> = unsupported()

        override fun <T : Any> sequence(returnType: KClass<T>): CloseableSequence<T> = unsupported()

        override fun rowsUpdated(): Long = unsupported()

        override fun generatedValues(vararg columns: String): Map<String, Any> = unsupported()
    }
}

private fun unsupported(): Nothing = error("The recording clients do not execute queries.")
