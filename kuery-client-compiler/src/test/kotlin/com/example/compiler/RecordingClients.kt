package com.example.compiler

import dev.hsbrysk.kuery.core.KueryBlockingClient
import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.core.KueryClientInternalApi
import dev.hsbrysk.kuery.core.SqlBuilder
import dev.hsbrysk.kuery.core.internal.SqlIds.id
import io.mockk.mockk

/**
 * Records the sqlId of every `sql` call so that compiled snippets can expose the ids the
 * compiler plugin injected. The terminal operations are irrelevant to those tests, so the
 * returned FetchSpec is a bare mockk stub.
 */
@OptIn(KueryClientInternalApi::class)
class RecordingKueryClient : KueryClient {
    val sqlIds = mutableListOf<String>()

    override fun sql(
        sqlId: String,
        block: SqlBuilder.() -> Unit,
    ): KueryClient.FetchSpec {
        sqlIds += sqlId
        return mockk()
    }

    override fun sql(block: SqlBuilder.() -> Unit): KueryClient.FetchSpec = sql(block.id(), block)
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
        return mockk()
    }

    override fun sql(block: SqlBuilder.() -> Unit): KueryBlockingClient.FetchSpec = sql(block.id(), block)
}
