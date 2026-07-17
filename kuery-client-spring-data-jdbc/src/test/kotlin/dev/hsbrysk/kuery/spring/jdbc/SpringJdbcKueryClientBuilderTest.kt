package dev.hsbrysk.kuery.spring.jdbc

import assertk.assertFailure
import assertk.assertions.hasMessage
import assertk.assertions.isInstanceOf
import org.junit.jupiter.api.Test

class SpringJdbcKueryClientBuilderTest {
    @Test
    fun `build throws IllegalArgumentException when dataSource is not specified`() {
        assertFailure {
            SpringJdbcKueryClient.builder().build()
        }.isInstanceOf(IllegalArgumentException::class).hasMessage("Specify dataSource.")
    }
}
