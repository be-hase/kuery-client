package dev.hsbrysk.kuery.core.observation

import dev.hsbrysk.kuery.core.KueryBlockingClient
import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.core.Sql
import io.micrometer.observation.Observation

/**
 * [Observation.Context] for [KueryClient] and [KueryBlockingClient]
 */
class KueryClientFetchContext(
    val sqlId: String,
    val sql: Sql,
) : Observation.Context()
