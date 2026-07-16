package dev.hsbrysk.kuery.core.internal

import dev.hsbrysk.kuery.core.KueryClientInternalApi
import dev.hsbrysk.kuery.core.SqlBuilder
import dev.hsbrysk.kuery.core.internal.SqlIds.id

/**
 * Stand-in for a real client entry point: it lives in the dev.hsbrysk.kuery package, so its
 * frame is skipped by SqlIds' stack walk exactly like DefaultSpring*KueryClient.sql().
 */
@OptIn(KueryClientInternalApi::class)
internal object FakeClientEntryPoint {
    fun sql(block: SqlBuilder.() -> Unit): String = block.id()
}
