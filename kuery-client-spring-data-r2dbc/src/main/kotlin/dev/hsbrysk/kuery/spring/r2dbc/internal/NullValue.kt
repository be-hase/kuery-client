package dev.hsbrysk.kuery.spring.r2dbc.internal

// Sentinel for a SQL NULL scalar value flowing through a Reactor pipeline, which cannot carry null.
// Emitted by the scalar mappers (SingleColumnRowMapper / ValueClassScalarRowMapper) and unwrapped
// at the terminal operators.
internal object NullValue

@Suppress("UNCHECKED_CAST")
internal fun <T : Any> Any.unwrapNullValue(): T? = if (this === NullValue) null else this as T
