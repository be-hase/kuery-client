package dev.hsbrysk.kuery.core

/**
 * [DslMarker] for the [SqlBuilder] DSL.
 *
 * It prevents accidentally calling methods of an outer [SqlBuilder] receiver from within a
 * nested builder block.
 */
@DslMarker
public annotation class SqlBuilderMarker
