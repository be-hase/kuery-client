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
    fun getName() {
        assertThat(target.name).isEqualTo("kuery.client.fetches")
    }

    @Test
    fun supportsContext() {
        assertThat(target.supportsContext(KueryClientFetchContext("id", Sql("")))).isTrue()
        assertThat(target.supportsContext(Observation.Context())).isFalse()
    }
}
