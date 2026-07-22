package dev.hsbrysk.kuery.core

// PoC note: this is a copy of kuery-client-core's SqlBuilder with the only KMP-incompatible
// piece removed — `@Language("sql")` (org.intellij.lang.annotations is a JVM-only artifact).
// A real KMP conversion would keep the annotation on the JVM target or drop it.

/**
 * DSL scope for building SQL.
 *
 * Add SQL fragments with [add] or [String.unaryPlus]; the fragments are joined with line breaks
 * to form the final statement. Thanks to the Kuery Client compiler plugin, any value interpolated
 * into those fragments (`$value`) is bound as a named parameter instead of being embedded in the
 * SQL text, which prevents SQL injection.
 */
@SqlBuilderMarker
public sealed interface SqlBuilder {
    /**
     * Adds a SQL fragment to the statement being built.
     */
    public fun add(sql: String)

    /**
     * Adds a SQL fragment to the statement being built. Operator shorthand for [add].
     */
    public operator fun String.unaryPlus()

    /**
     * Adds a SQL fragment to the statement being built, WITHOUT rewriting string interpolation
     * into bind parameters.
     */
    @DelicateKueryClientApi
    public fun addUnsafe(sql: String)

    /**
     * Binds [parameter] as a named parameter and returns the placeholder string (e.g. `:p0`)
     * to embed in the SQL fragment.
     */
    @DelicateKueryClientApi
    public fun bind(parameter: Any?): String
}
