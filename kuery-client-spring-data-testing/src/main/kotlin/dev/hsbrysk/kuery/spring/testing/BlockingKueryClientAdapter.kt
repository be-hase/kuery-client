package dev.hsbrysk.kuery.spring.testing

import dev.hsbrysk.kuery.core.KueryBlockingClient
import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.core.SqlBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.reflect.KClass

/**
 * Adapts a [KueryBlockingClient] to the [KueryClient] interface so that contract tests written
 * against the coroutine API also run on the blocking implementation.
 *
 * Terminal operations delegate 1:1 without switching threads, so the blocking call runs on the
 * caller's thread and thread-bound semantics (transactions, sqlId propagation) are preserved.
 * [flowMap]/[flow] iterate the underlying [KueryBlockingClient.FetchSpec.sequenceMap]/`sequence`
 * inside `use`, closing it both on full iteration and on collector cancellation.
 *
 * Passing the received block through to the delegate does not affect sqlId generation: the
 * compiler plugin only rewrites blocks written literally at the call site, so the sqlId captured
 * at the original call site survives this delegation.
 *
 * The blocking-only options ([KueryBlockingClient.FetchSpec.maxRows] and
 * [KueryBlockingClient.FetchSpec.queryTimeoutSeconds]) have no [KueryClient] counterpart and are
 * out of scope for contract tests; cover them in jdbc-only tests.
 */
class BlockingKueryClientAdapter(private val delegate: KueryBlockingClient) : KueryClient {
    override fun sql(
        sqlId: String,
        block: SqlBuilder.() -> Unit,
    ): KueryClient.FetchSpec = FetchSpecAdapter(delegate.sql(sqlId, block))

    override fun sql(block: SqlBuilder.() -> Unit): KueryClient.FetchSpec = FetchSpecAdapter(delegate.sql(block))

    @Suppress("TooManyFunctions")
    private class FetchSpecAdapter(private val delegate: KueryBlockingClient.FetchSpec) : KueryClient.FetchSpec {
        override fun fetchSize(fetchSize: Int): KueryClient.FetchSpec = FetchSpecAdapter(delegate.fetchSize(fetchSize))

        override suspend fun singleMap(): Map<String, Any?> = delegate.singleMap()

        override suspend fun singleMapOrNull(): Map<String, Any?>? = delegate.singleMapOrNull()

        override suspend fun <T : Any> single(returnType: KClass<T>): T = delegate.single(returnType)

        override suspend fun <T : Any> singleOrNull(returnType: KClass<T>): T? = delegate.singleOrNull(returnType)

        override suspend fun listMap(): List<Map<String, Any?>> = delegate.listMap()

        override suspend fun <T : Any> list(returnType: KClass<T>): List<T> = delegate.list(returnType)

        override fun flowMap(): Flow<Map<String, Any?>> = flow {
            delegate.sequenceMap().use { sequence ->
                sequence.forEach { emit(it) }
            }
        }

        override fun <T : Any> flow(returnType: KClass<T>): Flow<T> = flow {
            delegate.sequence(returnType).use { sequence ->
                sequence.forEach { emit(it) }
            }
        }

        override suspend fun rowsUpdated(): Long = delegate.rowsUpdated()

        override suspend fun generatedValues(vararg columns: String): Map<String, Any> =
            delegate.generatedValues(*columns)
    }
}
