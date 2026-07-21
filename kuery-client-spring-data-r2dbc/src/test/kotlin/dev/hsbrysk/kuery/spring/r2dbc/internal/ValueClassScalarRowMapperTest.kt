package dev.hsbrysk.kuery.spring.r2dbc.internal

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import io.r2dbc.spi.Readable
import org.junit.jupiter.api.Test
import org.springframework.core.convert.support.DefaultConversionService

/**
 * Direct unit tests for [ValueClassScalarRowMapper]'s driver typed-retrieval fallback, driving a
 * fake [Readable] instead of a real database. The R2DBC SPI signals "the column cannot be decoded
 * to the requested type" as an `IllegalArgumentException`; only that must degrade to the raw value,
 * while any other driver/connection fault must propagate.
 *
 * R2DBC-only (no jdbc mirror): the jdbc counterpart falls back on `SQLException`, a checked
 * exception with different semantics, and faking a full `ResultSet` would add little.
 */
class ValueClassScalarRowMapperTest {
    @JvmInline
    value class Amount(val value: Int)

    // A Readable whose single column reads back as [raw] with no type hint, and whose typed
    // retrieval is driven by [typed] so the fallback behaviour can be exercised deterministically.
    private class FakeReadable(
        private val raw: Any?,
        private val typed: (Class<*>) -> Any?,
    ) : Readable {
        @Suppress("UNCHECKED_CAST")
        override fun <T : Any?> get(
            index: Int,
            type: Class<T>,
        ): T? = typed(type) as T?

        override fun get(index: Int): Any? = raw

        override fun <T : Any?> get(
            name: String,
            type: Class<T>,
        ): T? = throw UnsupportedOperationException()

        override fun get(name: String): Any? = throw UnsupportedOperationException()
    }

    private fun mapper() = ValueClassScalarRowMapper(Amount::class, DefaultConversionService())

    @Test
    fun `driver typed retrieval falls back to the raw value on IllegalArgumentException`() {
        // The raw Long is coercible to the Int underlying by the ConversionService, but the driver
        // cannot decode the column to Int and signals it as IllegalArgumentException; retrieval must
        // degrade to the raw value rather than fail.
        // given
        val readable = FakeReadable(raw = 1L) { throw IllegalArgumentException("cannot decode to Integer") }

        // when & then
        assertThat(mapper().apply(readable)).isEqualTo(Amount(1))
    }

    @Test
    fun `an unexpected driver exception propagates from typed retrieval`() {
        // A driver/connection fault (here an IllegalStateException) is not the "cannot decode" signal
        // and must not be masked as a fall-back to the raw value.
        // given
        val readable = FakeReadable(raw = 1L) { throw IllegalStateException("connection reset") }

        // when & then
        assertFailure { mapper().apply(readable) }.isInstanceOf(IllegalStateException::class)
    }
}
