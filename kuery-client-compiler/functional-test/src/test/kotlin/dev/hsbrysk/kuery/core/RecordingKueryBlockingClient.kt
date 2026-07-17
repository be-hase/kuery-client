package dev.hsbrysk.kuery.core

import dev.hsbrysk.kuery.core.internal.SqlIds.id
import kotlin.reflect.KClass

/**
 * Records the sqlId of every `sql` call so that tests can observe the ids the compiler plugin
 * injected at the call sites. The terminal operations are irrelevant to those tests and simply
 * fail.
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

        private fun unsupported(): Nothing = error("RecordingKueryBlockingClient does not execute queries.")
    }
}
