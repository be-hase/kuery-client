package dev.hsbrysk.kuery.compiler.ir.misc

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

class TrimFoldingTest {
    @Test
    fun `a template with values mid-line folds like runtime trimIndent`() {
        assertFoldsLikeRuntime(
            fragments = listOf("\n                UPDATE user\n                SET name = ", "\n                "),
            valueCount = 1,
        )
    }

    @Test
    fun `a template with a value at line start folds like runtime trimIndent`() {
        assertFoldsLikeRuntime(
            fragments = listOf("\n    ", " AND foo\n    bar\n"),
            valueCount = 1,
        )
    }

    @Test
    fun `a template ending with a value folds like runtime trimIndent`() {
        // fragments.size == valueCount (no trailing fragment)
        assertFoldsLikeRuntime(
            fragments = listOf("\n  SELECT *\n  WHERE id = "),
            valueCount = 1,
        )
    }

    @Test
    fun `a template with consecutive values and blank lines folds like runtime trimIndent`() {
        assertFoldsLikeRuntime(
            fragments = listOf("\n   a ", "", "\n\n   b\n   "),
            valueCount = 3,
        )
    }

    @Test
    fun `a template with blank first and last lines folds like runtime trimIndent`() {
        assertFoldsLikeRuntime(
            fragments = listOf("   \n  x = ", "\n   \t"),
            valueCount = 1,
        )
    }

    @Test
    fun `a template with no values folds like runtime trimIndent`() {
        assertFoldsLikeRuntime(
            fragments = listOf("\n  SELECT *\n  FROM user\n"),
            valueCount = 0,
        )
    }

    @Test
    fun `a single-line template folds like runtime trimIndent`() {
        assertFoldsLikeRuntime(
            fragments = listOf("  SELECT ", ""),
            valueCount = 1,
        )
    }

    @Test
    fun `an empty template folds like runtime trimIndent`() {
        assertFoldsLikeRuntime(
            fragments = listOf(""),
            valueCount = 0,
        )
    }

    @Test
    fun `a value-only template folds like runtime trimIndent`() {
        assertFoldsLikeRuntime(
            fragments = listOf(""),
            valueCount = 1,
        )
    }

    @Test
    fun `foldOrNull returns null when a fragment contains the sentinel character`() {
        assertThat(TrimFolding.foldOrNull(listOf("a\u0000b"), 0)).isNull()
    }

    /**
     * The correctness contract: rendering `:pN` placeholders into the *folded* fragments must
     * produce exactly the same string as applying trimIndent at runtime to the string rendered
     * from the *original* fragments.
     */
    private fun assertFoldsLikeRuntime(
        fragments: List<String>,
        valueCount: Int,
    ) {
        val runtimeResult = render(fragments, valueCount).trimIndent()
        val folded = checkNotNull(TrimFolding.foldOrNull(fragments, valueCount)) {
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
