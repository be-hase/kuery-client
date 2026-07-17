package dev.hsbrysk.kuery.core.observation

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.observation.KueryClientObservationDocumentation.FETCH
import dev.hsbrysk.kuery.core.observation.internal.DefaultKueryClientFetchObservationConvention
import org.junit.jupiter.api.Test

class KueryClientObservationDocumentationTest {
    @Test
    fun `FETCH declares the default convention and its low and high cardinality key names`() {
        assertThat(FETCH.defaultConvention).isEqualTo(DefaultKueryClientFetchObservationConvention::class.java)
        assertThat(FETCH.lowCardinalityKeyNames.map { it.asString() }).isEqualTo(listOf("sql.id"))
        assertThat(FETCH.highCardinalityKeyNames.map { it.asString() }).isEqualTo(listOf("sql"))
    }
}
