package dev.hsbrysk.kuery.core.observation

import dev.hsbrysk.kuery.core.KueryBlockingClient
import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.core.Sql
import io.micrometer.observation.Observation

/**
 * [Observation.Context] for [KueryClient] and [KueryBlockingClient]
 */
public class KueryClientFetchContext(
    public val sqlId: String,
    public val sql: Sql,
) : Observation.Context()
