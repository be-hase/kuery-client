package dev.hsbrysk.kuery.core.observation

import dev.hsbrysk.kuery.core.KueryBlockingClient
import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.core.Sql
import io.micrometer.observation.Observation

/**
 * [Observation.Context] for the fetch observations recorded by [KueryClient] and
 * [KueryBlockingClient].
 *
 * @property sqlId the ID that identifies the executed query
 * @property sql the executed SQL (with named placeholders; bound values are held separately
 * in [Sql.parameters])
 */
public class KueryClientFetchContext(
    public val sqlId: String,
    public val sql: Sql,
) : Observation.Context()
