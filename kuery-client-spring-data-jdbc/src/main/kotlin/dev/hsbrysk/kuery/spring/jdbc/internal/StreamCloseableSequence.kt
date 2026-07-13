package dev.hsbrysk.kuery.spring.jdbc.internal

import dev.hsbrysk.kuery.core.CloseableSequence
import java.util.stream.Stream

/**
 * Wraps this [Stream] into a [CloseableSequence] that preserves the stream's close handlers.
 *
 * The stream is closed when the iteration reaches the end, when [Iterator.hasNext] / [Iterator.next]
 * throws, or when [CloseableSequence.close] is called explicitly.
 */
internal fun <T> Stream<T>.asCloseableSequence(): CloseableSequence<T> = StreamCloseableSequence(this)

private class StreamCloseableSequence<T>(private val stream: Stream<T>) : CloseableSequence<T> {
    private var closed = false
    private var iterated = false

    override fun iterator(): Iterator<T> {
        check(!iterated) { "This sequence can be iterated only once." }
        check(!closed) { "This sequence is already closed." }
        iterated = true
        val iterator = stream.iterator()
        // The underlying stream iterator throws NoSuchElementException when exhausted.
        @Suppress("IteratorNotThrowingNoSuchElementException")
        return object : Iterator<T> {
            override fun hasNext(): Boolean = closeOnFailure {
                val hasNext = iterator.hasNext()
                if (!hasNext) {
                    close()
                }
                hasNext
            }

            override fun next(): T = closeOnFailure {
                iterator.next()
            }
        }
    }

    override fun close() {
        if (!closed) {
            closed = true
            stream.close()
        }
    }

    private inline fun <R> closeOnFailure(block: () -> R): R = try {
        block()
    } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
        close()
        throw e
    }
}
