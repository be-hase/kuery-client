package dev.hsbrysk.kuery.core.observation

import dev.hsbrysk.kuery.core.KueryBlockingClient
import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.core.observation.internal.DefaultKueryClientFetchObservationConvention
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationConvention

/**
 * [ObservationConvention] for [KueryClient] and [KueryBlockingClient]
 */
interface KueryClientFetchObservationConvention : ObservationConvention<KueryClientFetchContext> {
    override fun getName(): String {
        return "kuery.client.fetches"
    }

    override fun supportsContext(context: Observation.Context): Boolean {
        return context is KueryClientFetchContext
    }

    companion object {
        fun default(): KueryClientFetchObservationConvention {
            return DefaultKueryClientFetchObservationConvention()
        }
    }
}
