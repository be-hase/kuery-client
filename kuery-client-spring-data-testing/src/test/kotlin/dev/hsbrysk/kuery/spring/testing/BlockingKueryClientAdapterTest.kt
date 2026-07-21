package dev.hsbrysk.kuery.spring.testing

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import dev.hsbrysk.kuery.core.CloseableSequence
import dev.hsbrysk.kuery.core.KueryBlockingClient
import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.core.KueryClientInternalApi
import dev.hsbrysk.kuery.core.SqlBuilder
import dev.hsbrysk.kuery.core.internal.SqlIds
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class BlockingKueryClientAdapterTest {
    private val fetchSpec = mockk<KueryBlockingClient.FetchSpec>()
    private val blockingClient = mockk<KueryBlockingClient>()

    // Typed as KueryClient so the compiler plugin rewrites the literal sql { } call sites below,
    // exactly as it does for contract tests.
    private val adapter: KueryClient = BlockingKueryClientAdapter(blockingClient)

    @Test
    fun `explicit sqlId is passed through to the delegate`() {
        val sqlIdSlot = slot<String>()
        every { blockingClient.sql(capture(sqlIdSlot), any()) } returns fetchSpec

        adapter.sql("explicit-id") { +"SELECT 1" }

        assertThat(sqlIdSlot.captured).isEqualTo("explicit-id")
    }

    @OptIn(KueryClientInternalApi::class)
    @Test
    fun `auto-generated sqlId from the call site survives delegation`() {
        // given
        val blockSlot = slot<SqlBuilder.() -> Unit>()
        every { blockingClient.sql(capture(blockSlot)) } returns fetchSpec

        // when: the compiler plugin wraps this literal block with the sqlId of this test method;
        // the adapter forwards the block as a variable, which must not re-wrap it
        adapter.sql { +"SELECT 1" }

        // then
        val forwardedSqlId = with(SqlIds) { blockSlot.captured.id() }
        assertThat(forwardedSqlId).isEqualTo(
            "dev.hsbrysk.kuery.spring.testing.BlockingKueryClientAdapterTest" +
                ".auto-generated sqlId from the call site survives delegation",
        )
    }

    @Test
    fun `fetchSize returns an adapter that delegates single to the resized fetch spec`() = runTest {
        // given
        val resizedFetchSpec = mockk<KueryBlockingClient.FetchSpec>()
        every { blockingClient.sql(any()) } returns fetchSpec
        every { fetchSpec.fetchSize(10) } returns resizedFetchSpec
        every { resizedFetchSpec.single(String::class) } returns "row"

        // when & then
        val result: String = adapter.sql { +"SELECT 1" }.fetchSize(10).single(String::class)
        assertThat(result).isEqualTo("row")
    }

    @Test
    fun `flow closes the sequence after full collection`() = runTest {
        // given
        val sequence = TrackingSequence(listOf("a", "b"))
        every { blockingClient.sql(any()) } returns fetchSpec
        every { fetchSpec.sequence(String::class) } returns sequence

        // when
        val result = adapter.sql { +"SELECT 1" }.flow(String::class).toList()

        // then
        assertThat(result).containsExactly("a", "b")
        assertThat(sequence.closed).isTrue()
    }

    @Test
    fun `flowMap closes the sequence after full collection`() = runTest {
        // given
        val sequence = TrackingSequence(listOf(mapOf<String, Any?>("id" to 1L)))
        every { blockingClient.sql(any()) } returns fetchSpec
        every { fetchSpec.sequenceMap() } returns sequence

        // when
        val result = adapter.sql { +"SELECT 1" }.flowMap().toList()

        // then
        assertThat(result).containsExactly(mapOf<String, Any?>("id" to 1L))
        assertThat(sequence.closed).isTrue()
    }

    @Test
    fun `flow closes the sequence when collection stops early`() = runTest {
        // given
        val sequence = TrackingSequence(listOf("a", "b", "c"))
        every { blockingClient.sql(any()) } returns fetchSpec
        every { fetchSpec.sequence(String::class) } returns sequence

        // when
        val result = adapter.sql { +"SELECT 1" }.flow(String::class).take(1).toList()

        // then
        assertThat(result).containsExactly("a")
        assertThat(sequence.closed).isTrue()
    }

    @Test
    fun `flow closes the sequence when the collector throws`() = runTest {
        // given
        val sequence = TrackingSequence(listOf("a", "b"))
        every { blockingClient.sql(any()) } returns fetchSpec
        every { fetchSpec.sequence(String::class) } returns sequence

        // when
        assertFailure {
            adapter.sql { +"SELECT 1" }.flow(String::class).collect { error("collector failure") }
        }.isInstanceOf(IllegalStateException::class).hasMessage("collector failure")

        // then
        assertThat(sequence.closed).isTrue()
    }

    @Test
    fun `flow closes the sequence when fetching the next element throws`() = runTest {
        // given
        val sequence = TrackingSequence(listOf("a", "b"), throwOnNextAt = 1)
        every { blockingClient.sql(any()) } returns fetchSpec
        every { fetchSpec.sequence(String::class) } returns sequence

        // when
        assertFailure {
            adapter.sql { +"SELECT 1" }.flow(String::class).toList()
        }.isInstanceOf(IllegalStateException::class).hasMessage("fetch failure")

        // then
        assertThat(sequence.closed).isTrue()
    }

    private class TrackingSequence<T>(
        private val elements: List<T>,
        private val throwOnNextAt: Int? = null,
    ) : CloseableSequence<T> {
        var closed = false
            private set

        override fun close() {
            closed = true
        }

        override fun iterator(): Iterator<T> = object : Iterator<T> {
            private var index = 0

            override fun hasNext(): Boolean = index < elements.size

            override fun next(): T {
                if (!hasNext()) {
                    throw NoSuchElementException()
                }
                check(index != throwOnNextAt) { "fetch failure" }
                return elements[index++]
            }
        }
    }
}
