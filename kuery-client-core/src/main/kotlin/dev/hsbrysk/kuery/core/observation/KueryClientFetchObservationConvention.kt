package dev.hsbrysk.kuery.core.observation

import dev.hsbrysk.kuery.core.KueryBlockingClient
import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.core.observation.internal.DefaultKueryClientFetchObservationConvention
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationConvention

/**
 * [ObservationConvention] for [KueryClient] and [KueryBlockingClient]
 */
public interface KueryClientFetchObservationConvention : ObservationConvention<KueryClientFetchContext> {
    override fun getName(): String = "kuery.client.fetches"

    override fun supportsContext(context: Observation.Context): Boolean = context is KueryClientFetchContext

    public companion object {
        public fun default(): KueryClientFetchObservationConvention = DefaultKueryClientFetchObservationConvention()
    }
}
