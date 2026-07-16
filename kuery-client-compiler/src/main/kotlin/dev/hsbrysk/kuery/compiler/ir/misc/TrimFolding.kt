package dev.hsbrysk.kuery.compiler.ir.misc

/**
 * Compile-time computation of `trimIndent()` over an interpolated SQL template. At runtime the
 * interpolated values are replaced by `:pN` placeholders, which contain neither newlines nor
 * whitespace, so per-line leading whitespace and blank-line detection — all that trimIndent
 * looks at — are fully determined by the constant fragments. The trim result can therefore be
 * computed at compile time by standing in a single non-whitespace sentinel character for each
 * value, applying the stdlib trimIndent to the joined string, and splitting it back into
 * fragments.
 */
internal object TrimFolding {
    // NUL never appears in realistic SQL text and is not whitespace, so lines containing a value
    // stay non-blank exactly like their runtime `:pN` counterparts.
    private const val SENTINEL = '\u0000'

    /**
     * Returns the trim-indented fragments (`valueCount + 1` elements), or null when equivalence
     * with the runtime behavior cannot be guaranteed and the caller must fall back to runtime
     * trimming.
     */
    fun foldOrNull(
        fragments: List<String>,
        valueCount: Int,
    ): List<String>? {
        if (fragments.any { SENTINEL in it }) {
            return null
        }

        // Join in the same shape as DefaultSqlBuilder.interpolate: fragment, value, fragment, ...
        // StringConcatenationProcessor emits one fragment per value plus an optional trailing
        // fragment, so padding to valueCount + 1 slots only ever adds that missing last one.
        val joined = List(valueCount + 1) { fragments.getOrElse(it) { "" } }
            .joinToString(SENTINEL.toString())

        val folded = joined.trimIndent().split(SENTINEL)
        // trimIndent only removes whitespace and blank first/last lines, none of which can
        // contain the sentinel, so the value positions are always preserved.
        check(folded.size == valueCount + 1) {
            "Trim folding produced ${folded.size} fragments for $valueCount values. " +
                "There might be an issue with the kuery-client compiler plugin."
        }
        return folded
    }
}
