package dev.hsbrysk.kuery.spring.r2dbc

import assertk.assertFailure
import assertk.assertions.hasMessage
import assertk.assertions.isInstanceOf
import org.junit.jupiter.api.Test

class SpringR2dbcKueryClientBuilderTest {
    @Test
    fun `build throws IllegalArgumentException when connectionFactory is not specified`() {
        assertFailure {
            SpringR2dbcKueryClient.builder().build()
        }.isInstanceOf(IllegalArgumentException::class).hasMessage("Specify connectionFactory.")
    }
}
