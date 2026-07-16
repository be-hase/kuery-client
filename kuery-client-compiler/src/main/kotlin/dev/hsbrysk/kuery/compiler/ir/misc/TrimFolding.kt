package dev.hsbrysk.kuery.compiler.ir.misc

internal sealed interface TrimOperation {
    fun apply(input: String): String

    data object TrimIndent : TrimOperation {
        override fun apply(input: String): String = input.trimIndent()
    }

    data class TrimMargin(val marginPrefix: String) : TrimOperation {
        override fun apply(input: String): String = input.trimMargin(marginPrefix)
    }
}

/**
 * Compile-time folding of `trimIndent()` / `trimMargin(...)` applied to an interpolated SQL
 * template. At runtime the interpolated values are replaced by `:pN` placeholders, which contain
 * neither newlines nor whitespace, so per-line leading whitespace and blank-line detection — all
 * that the trim functions look at — are fully determined by the constant fragments. The trim
 * result can therefore be computed at compile time by standing in a single non-whitespace
 * sentinel character for each value, applying the stdlib trim to the joined string, and splitting
 * it back into fragments.
 */
internal object TrimFolding {
    // NUL never appears in realistic SQL text and is not whitespace, so lines containing a value
    // stay non-blank exactly like their runtime `:pN` counterparts.
    private const val SENTINEL = '\u0000'

    /**
     * Returns the folded fragments (`valueCount + 1` elements), or null when equivalence with the
     * runtime behavior cannot be guaranteed and the caller must fall back to runtime trimming.
     */
    fun foldOrNull(
        fragments: List<String>,
        valueCount: Int,
        operation: TrimOperation,
    ): List<String>? {
        if (fragments.any { SENTINEL in it }) {
            return null
        }
        if (operation is TrimOperation.TrimMargin) {
            // A blank prefix throws IllegalArgumentException at runtime, which must be preserved.
            // A prefix containing ':' can match into the runtime `:pN` placeholder text, in which
            // case the sentinel stand-in would diverge from the runtime result.
            val prefix = operation.marginPrefix
            if (prefix.isBlank() || ':' in prefix || SENTINEL in prefix) {
                return null
            }
        }

        // Join in the same shape as DefaultSqlBuilder.interpolate: fragment, value, fragment, ...
        val joined = buildString {
            repeat(valueCount) { index ->
                append(fragments.getOrElse(index) { "" })
                append(SENTINEL)
            }
            fragments.drop(valueCount).forEach { append(it) }
        }

        val trimmed = runCatching { operation.apply(joined) }.getOrElse { return null }

        // The trim functions only remove whitespace, the margin prefix, and blank first/last
        // lines, none of which can contain the sentinel — but re-check defensively.
        val folded = trimmed.split(SENTINEL)
        if (folded.size != valueCount + 1) {
            return null
        }
        return folded
    }
}
