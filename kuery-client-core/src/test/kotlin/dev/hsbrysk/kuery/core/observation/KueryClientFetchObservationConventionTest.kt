package dev.hsbrysk.kuery.core.observation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import dev.hsbrysk.kuery.core.Sql
import io.micrometer.observation.Observation
import org.junit.jupiter.api.Test

class KueryClientFetchObservationConventionTest {
    private val target = KueryClientFetchObservationConvention.default()

    @Test
    fun `name returns the fixed observation name`() {
        assertThat(target.name).isEqualTo("kuery.client.fetches")
    }

    @Test
    fun `supportsContext returns true only for KueryClientFetchContext`() {
        assertThat(target.supportsContext(KueryClientFetchContext("id", Sql("")))).isTrue()
        assertThat(target.supportsContext(Observation.Context())).isFalse()
    }
}
