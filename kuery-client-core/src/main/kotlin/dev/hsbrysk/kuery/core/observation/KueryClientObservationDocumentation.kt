package dev.hsbrysk.kuery.core.observation

import dev.hsbrysk.kuery.core.KueryBlockingClient
import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.core.observation.internal.DefaultKueryClientFetchObservationConvention
import io.micrometer.common.docs.KeyName
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationConvention
import io.micrometer.observation.docs.ObservationDocumentation

/**
 * [ObservationDocumentation] for [KueryClient] and [KueryBlockingClient]
 */
enum class KueryClientObservationDocumentation : ObservationDocumentation {
    FETCH {
        override fun getDefaultConvention(): Class<out ObservationConvention<out Observation.Context>> =
            DefaultKueryClientFetchObservationConvention::class.java

        override fun getLowCardinalityKeyNames(): Array<KeyName> =
            DefaultKueryClientFetchObservationConvention.getLowCardinalityKeyNames()

        override fun getHighCardinalityKeyNames(): Array<KeyName> =
            DefaultKueryClientFetchObservationConvention.getHighCardinalityKeyNames()
    },
}
