package dev.hsbrysk.kuery.core.observation

import dev.hsbrysk.kuery.core.KueryBlockingClient
import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.core.observation.internal.DefaultKueryClientFetchObservationConvention
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationConvention

/**
 * [ObservationConvention] for the fetch observations recorded by [KueryClient] and
 * [KueryBlockingClient].
 *
 * Implement this and pass it to the client builder's `observationConvention(...)` to customize
 * the observation name (`kuery.client.fetches` by default) or the recorded tags.
 */
public interface KueryClientFetchObservationConvention : ObservationConvention<KueryClientFetchContext> {
    override fun getName(): String = "kuery.client.fetches"

    override fun supportsContext(context: Observation.Context): Boolean = context is KueryClientFetchContext

    public companion object {
        /**
         * Returns the default convention, which tags each observation with `sql.id`
         * (low cardinality) and `sql` (high cardinality).
         */
        public fun default(): KueryClientFetchObservationConvention = DefaultKueryClientFetchObservationConvention()
    }
}
