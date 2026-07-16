package com.example.core

import dev.hsbrysk.kuery.core.SqlBuilder
import dev.hsbrysk.kuery.core.internal.FakeClientEntryPoint

/**
 * Simulates the common decorator pattern `class Guard(client: KueryBlockingClient) :
 * KueryBlockingClient by client` (transaction guards etc.) sitting between the repository and
 * the client.
 */
internal class DelegatingClient {
    fun sql(block: SqlBuilder.() -> Unit): String = FakeClientEntryPoint.sql(block)
}

internal class RepositoryBehindDelegation(private val client: DelegatingClient = DelegatingClient()) {
    fun find(): String = client.sql { }
}
