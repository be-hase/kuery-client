package dev.hsbrysk.kuery.compiler.ir.misc

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

class TrimFoldingTest {
    @Test
    fun `trimIndent with values mid-line`() {
        assertFoldsLikeRuntime(
            fragments = listOf("\n                UPDATE user\n                SET name = ", "\n                "),
            valueCount = 1,
            operation = TrimOperation.TrimIndent,
        )
    }

    @Test
    fun `trimIndent with a value at line start`() {
        assertFoldsLikeRuntime(
            fragments = listOf("\n    ", " AND foo\n    bar\n"),
            valueCount = 1,
            operation = TrimOperation.TrimIndent,
        )
    }

    @Test
    fun `trimIndent with a trailing value`() {
        // fragments.size == valueCount (no trailing fragment)
        assertFoldsLikeRuntime(
            fragments = listOf("\n  SELECT *\n  WHERE id = "),
            valueCount = 1,
            operation = TrimOperation.TrimIndent,
        )
    }

    @Test
    fun `trimIndent with consecutive values and blank lines`() {
        assertFoldsLikeRuntime(
            fragments = listOf("\n   a ", "", "\n\n   b\n   "),
            valueCount = 3,
            operation = TrimOperation.TrimIndent,
        )
    }

    @Test
    fun `trimIndent with blank first and last lines`() {
        assertFoldsLikeRuntime(
            fragments = listOf("   \n  x = ", "\n   \t"),
            valueCount = 1,
            operation = TrimOperation.TrimIndent,
        )
    }

    @Test
    fun `trimIndent with no values`() {
        assertFoldsLikeRuntime(
            fragments = listOf("\n  SELECT *\n  FROM user\n"),
            valueCount = 0,
            operation = TrimOperation.TrimIndent,
        )
    }

    @Test
    fun `trimIndent single line`() {
        assertFoldsLikeRuntime(
            fragments = listOf("  SELECT ", ""),
            valueCount = 1,
            operation = TrimOperation.TrimIndent,
        )
    }

    @Test
    fun `trimMargin with default prefix`() {
        assertFoldsLikeRuntime(
            fragments = listOf("\n      |SELECT *\n      |WHERE id = ", "\n      "),
            valueCount = 1,
            operation = TrimOperation.TrimMargin("|"),
        )
    }

    @Test
    fun `trimMargin with custom prefix`() {
        assertFoldsLikeRuntime(
            fragments = listOf("\n      > SELECT *\n      > WHERE id = ", "\n      "),
            valueCount = 1,
            operation = TrimOperation.TrimMargin("> "),
        )
    }

    @Test
    fun `trimMargin with lines lacking the prefix`() {
        assertFoldsLikeRuntime(
            fragments = listOf("\n  |a = ", "\n  no margin here\n  |b\n"),
            valueCount = 1,
            operation = TrimOperation.TrimMargin("|"),
        )
    }

    @Test
    fun `trimMargin with a blank prefix falls back`() {
        assertThat(
            TrimFolding.foldOrNull(listOf("a"), 0, TrimOperation.TrimMargin(" ")),
        ).isNull()
    }

    @Test
    fun `trimMargin with a colon prefix falls back`() {
        assertThat(
            TrimFolding.foldOrNull(listOf("\n:a = ", "\n"), 1, TrimOperation.TrimMargin(":")),
        ).isNull()
    }

    @Test
    fun `trimMargin with a sentinel prefix falls back`() {
        assertThat(
            TrimFolding.foldOrNull(listOf("a"), 0, TrimOperation.TrimMargin("\u0000")),
        ).isNull()
    }

    @Test
    fun `fragment containing the sentinel falls back`() {
        assertThat(
            TrimFolding.foldOrNull(listOf("a\u0000b"), 0, TrimOperation.TrimIndent),
        ).isNull()
    }

    /**
     * The correctness contract: rendering `:pN` placeholders into the *folded* fragments must
     * produce exactly the same string as applying the trim function at runtime to the string
     * rendered from the *original* fragments.
     */
    private fun assertFoldsLikeRuntime(
        fragments: List<String>,
        valueCount: Int,
        operation: TrimOperation,
    ) {
        val runtimeResult = operation.apply(render(fragments, valueCount))
        val folded = checkNotNull(TrimFolding.foldOrNull(fragments, valueCount, operation)) {
            "expected folding to succeed for $fragments"
        }
        assertThat(folded.size).isEqualTo(valueCount + 1)
        assertThat(render(folded, valueCount)).isEqualTo(runtimeResult)
    }

    // Same shape as DefaultSqlBuilder.interpolate with placeholder text substituted for values.
    private fun render(
        fragments: List<String>,
        valueCount: Int,
    ): String = buildString {
        repeat(valueCount) { index ->
            append(fragments.getOrElse(index) { "" })
            append(":p").append(index)
        }
        fragments.drop(valueCount).forEach { append(it) }
    }
}
