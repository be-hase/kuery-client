package dev.hsbrysk.kuery.spring.jdbc.internal

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import org.junit.jupiter.api.Test
import java.util.Spliterators
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.stream.Stream
import java.util.stream.StreamSupport

class StreamCloseableSequenceTest {
    @Test
    fun `iterator throws ISE when the sequence is already closed`() {
        val sequence = Stream.of("a", "b").asCloseableSequence()
        sequence.close()

        assertFailure { sequence.iterator() }
            .isInstanceOf(IllegalStateException::class)
            .hasMessage("This sequence is already closed.")
    }

    @Test
    fun `close is idempotent`() {
        val closeCount = AtomicInteger()
        val sequence = Stream.of("a", "b").onClose { closeCount.incrementAndGet() }.asCloseableSequence()

        sequence.close()
        sequence.close()

        assertThat(closeCount.get()).isEqualTo(1)
    }

    @Test
    fun `hasNext stays false after exhaustion without touching the closed stream`() {
        val iterator = resultSetLikeStream("a", "b").asCloseableSequence().iterator()

        assertThat(iterator.next()).isEqualTo("a")
        assertThat(iterator.next()).isEqualTo("b")
        assertThat(iterator.hasNext()).isFalse()

        // Iterator.hasNext must be idempotent; the JDK stream-iterator adapter does not cache
        // exhaustion and would re-touch the (already closed) underlying resource.
        assertThat(iterator.hasNext()).isFalse()
    }

    @Test
    fun `next after exhaustion throws NoSuchElementException`() {
        val iterator = resultSetLikeStream("a").asCloseableSequence().iterator()

        assertThat(iterator.next()).isEqualTo("a")
        assertThat(iterator.hasNext()).isFalse()

        assertFailure { iterator.next() }.isInstanceOf(NoSuchElementException::class)
    }

    @Test
    fun `explicit close after full iteration does not close the stream twice`() {
        val closeCount = AtomicInteger()
        val sequence = Stream.of("a", "b").onClose { closeCount.incrementAndGet() }.asCloseableSequence()

        // Reaching the end of the iteration closes the underlying stream.
        assertThat(sequence.toList()).isEqualTo(listOf("a", "b"))
        assertThat(closeCount.get()).isEqualTo(1)

        sequence.close()

        assertThat(closeCount.get()).isEqualTo(1)
    }

    /**
     * Models `JdbcTemplate.queryForStream`: a stream backed by a resource (ResultSet) that
     * throws when accessed after the stream's close handler has run.
     */
    private fun resultSetLikeStream(vararg values: String): Stream<String> {
        var closed = false
        val spliterator = object : Spliterators.AbstractSpliterator<String>(values.size.toLong(), 0) {
            private var index = 0

            override fun tryAdvance(action: Consumer<in String>): Boolean {
                check(!closed) { "The ResultSet is already closed." }
                if (index >= values.size) {
                    return false
                }
                action.accept(values[index++])
                return true
            }
        }
        return StreamSupport.stream(spliterator, false).onClose { closed = true }
    }
}
