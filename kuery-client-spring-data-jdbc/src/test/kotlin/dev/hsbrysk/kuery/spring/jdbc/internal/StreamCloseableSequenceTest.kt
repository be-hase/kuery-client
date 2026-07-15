package dev.hsbrysk.kuery.spring.jdbc.internal

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Stream

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
    fun `explicit close after full iteration does not close the stream twice`() {
        val closeCount = AtomicInteger()
        val sequence = Stream.of("a", "b").onClose { closeCount.incrementAndGet() }.asCloseableSequence()

        // Reaching the end of the iteration closes the underlying stream.
        assertThat(sequence.toList()).isEqualTo(listOf("a", "b"))
        assertThat(closeCount.get()).isEqualTo(1)

        sequence.close()

        assertThat(closeCount.get()).isEqualTo(1)
    }
}
