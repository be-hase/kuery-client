package dev.hsbrysk.kuery.core

/**
 * A [Sequence] backed by an underlying resource (e.g., an open JDBC ResultSet) that must be released.
 *
 * The resource is closed automatically when the iteration reaches the end, or when an exception is
 * thrown during the iteration. If you stop iterating midway (e.g., with `take` or `first`), close it
 * explicitly with [close], typically via [use]:
 *
 * ```kotlin
 * client.sql { +"SELECT * FROM users" }.sequence<User>().use { seq ->
 *     seq.first()
 * }
 * ```
 *
 * This sequence can be iterated only once.
 */
interface CloseableSequence<out T> :
    Sequence<T>,
    AutoCloseable
