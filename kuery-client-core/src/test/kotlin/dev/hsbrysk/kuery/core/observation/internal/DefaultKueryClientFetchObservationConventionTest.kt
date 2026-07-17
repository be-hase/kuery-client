package dev.hsbrysk.kuery.core.observation.internal

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.Sql
import dev.hsbrysk.kuery.core.observation.KueryClientFetchContext
import org.junit.jupiter.api.Test

class DefaultKueryClientFetchObservationConventionTest {
    private val target = DefaultKueryClientFetchObservationConvention()

    @Test
    fun `getLowCardinalityKeyValues returns only the sql id key value`() {
        // given
        val ctx = KueryClientFetchContext("id", Sql("body"))

        // when
        val result = target.getLowCardinalityKeyValues(ctx)

        // then
        assertThat(result.toList().size).isEqualTo(1)
        assertThat(result.first().key).isEqualTo("sql.id")
        assertThat(result.first().value).isEqualTo("id")
    }

    @Test
    fun `getHighCardinalityKeyValues returns only the sql key holding the body`() {
        // given
        val ctx = KueryClientFetchContext("id", Sql("body"))

        // when
        val result = target.getHighCardinalityKeyValues(ctx)

        // then
        assertThat(result.toList().size).isEqualTo(1)
        assertThat(result.first().key).isEqualTo("sql")
        assertThat(result.first().value).isEqualTo("body")
    }
}
