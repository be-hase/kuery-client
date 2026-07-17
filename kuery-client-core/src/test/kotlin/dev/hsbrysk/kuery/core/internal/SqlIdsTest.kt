package dev.hsbrysk.kuery.core.internal

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.DefaultSqlBuilder
import dev.hsbrysk.kuery.core.KueryClientInternalApi
import dev.hsbrysk.kuery.core.SqlBuilder
import dev.hsbrysk.kuery.core.internal.SqlIds.id
import org.junit.jupiter.api.Test

@OptIn(KueryClientInternalApi::class)
class SqlIdsTest {
    @Test
    fun `a block wrapped by the compiler plugin carries the injected sqlId`() {
        // given
        val block = sqlIdProvidingBlock("com.example.UserRepository.findById") { }

        // then
        assertThat(block.id()).isEqualTo("com.example.UserRepository.findById")
    }

    @Test
    fun `a wrapped block delegates its invocation`() {
        // given
        val invocations = mutableListOf<SqlBuilder>()
        val block = sqlIdProvidingBlock("id") { invocations += this }
        val sqlBuilder = DefaultSqlBuilder()

        // when
        block(sqlBuilder)

        // then
        assertThat(invocations).containsExactly(sqlBuilder)
    }

    @Test
    fun `a plain block resolves to NONE`() {
        // given
        val block: SqlBuilder.() -> Unit = { }

        // then
        assertThat(block.id()).isEqualTo("NONE")
    }
}
