package dev.hsbrysk.kuery.core

import dev.hsbrysk.kuery.core.KueryBlockingClient.FetchSpec
import kotlin.reflect.KClass

/**
 * A blocking SQL client.
 *
 * SQL is written with plain Kotlin string interpolation inside the [sql] block; the Kuery Client
 * compiler plugin rewrites interpolated runtime values into named bind parameters, so they are
 * never concatenated into the SQL text. Compile-time `String` and `Char` constants are expanded
 * into the SQL text instead.
 *
 * ```kotlin
 * val user: User = client.sql { +"SELECT * FROM users WHERE user_id = $userId" }.single()
 * ```
 *
 * Implementations are expected to be thread-safe; a single instance can be shared across the
 * whole application.
 *
 * For a coroutine-based (R2DBC) counterpart, see [KueryClient].
 */
public interface KueryBlockingClient {
    /**
     * Builds SQL using the received [SqlBuilder] block and returns a [FetchSpec] to obtain the
     * execution results.
     *
     * Note that the statement is not executed until one of the terminal operations on
     * [FetchSpec] is invoked.
     *
     * @param sqlId an ID that uniquely identifies the query, used for purposes such as metrics
     * @param block [SqlBuilder] block that constructs the SQL
     * @return a [FetchSpec] for retrieving the execution results
     */
    public fun sql(
        sqlId: String,
        block: SqlBuilder.() -> Unit,
    ): FetchSpec

    /**
     * Builds SQL using the received [SqlBuilder] block and returns a [FetchSpec] to obtain the
     * execution results.
     *
     * Note that the statement is not executed until one of the terminal operations on
     * [FetchSpec] is invoked.
     *
     * The sqlId is derived from the enclosing declaration of the call site at compile time by
     * the compiler plugin and used when auto sqlId generation is enabled. `"NONE"` is used when
     * auto sqlId generation is disabled, when the block is passed via a variable rather than
     * written literally at the call site, or when the call site was not compiled with the
     * compiler plugin.
     *
     * @param block [SqlBuilder] block that constructs the SQL
     * @return a [FetchSpec] for retrieving the execution results
     */
    public fun sql(block: SqlBuilder.() -> Unit): FetchSpec

    /**
     * Specifies how to execute the built SQL and retrieve the results.
     *
     * Each terminal operation ([singleMap], [single], [list], [sequence], [rowsUpdated],
     * [generatedValues], ...) executes the statement when invoked.
     *
     * Rows are mapped to the specified type as follows: simple scalar types (numbers, strings,
     * date/time types, ...) are read from the first column of the row, while other types
     * (e.g. data classes) are mapped by matching column names to constructor parameters.
     */
    @Suppress("TooManyFunctions")
    public interface FetchSpec {
        /**
         * Sets the fetch size (the number of rows fetched from the database at a time) to use
         * when executing this query.
         *
         * @return a new [FetchSpec] with the given fetch size applied
         */
        public fun fetchSize(fetchSize: Int): FetchSpec

        /**
         * Sets the maximum number of rows to return from this query.
         *
         * @return a new [FetchSpec] with the given limit applied
         */
        public fun maxRows(maxRows: Int): FetchSpec

        /**
         * Sets the query timeout (in seconds) for this query.
         *
         * @return a new [FetchSpec] with the given timeout applied
         */
        public fun queryTimeoutSeconds(queryTimeoutSeconds: Int): FetchSpec

        /**
         * Executes the query and returns exactly one row as a map keyed by column name.
         *
         * Fails with an exception if the query returns no rows or more than one row.
         */
        public fun singleMap(): Map<String, Any?>

        /**
         * Executes the query and returns at most one row as a map keyed by column name, or
         * `null` if the query returns no rows.
         *
         * Fails with an exception if the query returns more than one row.
         */
        public fun singleMapOrNull(): Map<String, Any?>?

        /**
         * Executes the query and returns exactly one row converted to [returnType].
         *
         * Fails with an exception if the query returns no rows, more than one row, or a single
         * scalar value that is SQL NULL.
         */
        public fun <T : Any> single(returnType: KClass<T>): T

        /**
         * Executes the query and returns at most one row converted to [returnType], or `null`
         * if the query returns no rows.
         *
         * Fails with an exception if the query returns more than one row.
         */
        public fun <T : Any> singleOrNull(returnType: KClass<T>): T?

        /**
         * Executes the query and returns all rows as a list of maps keyed by column name.
         */
        public fun listMap(): List<Map<String, Any?>>

        /**
         * Executes the query and returns all rows converted to [returnType].
         *
         * When fetching a simple scalar type from a single-column query, SQL NULL is kept as a null
         * element even though this cannot be expressed in the `List<T>` type.
         */
        public fun <T : Any> list(returnType: KClass<T>): List<T>

        /**
         * Executes the query and returns each row as a map keyed by column name, as a sequence
         * that streams rows without accumulating them all in memory.
         *
         * The returned sequence is backed by an open JDBC ResultSet; iterate it within an active
         * transaction. It is closed automatically when fully iterated or when fetching the next
         * element throws; exceptions thrown by your own processing code do not close it. Unless
         * you fully iterate the sequence, close it explicitly (e.g., with [use]). It can be
         * iterated only once. See [CloseableSequence].
         */
        public fun sequenceMap(): CloseableSequence<Map<String, Any?>>

        /**
         * Executes the query and returns each row converted to [returnType], as a sequence that
         * streams rows without accumulating them all in memory.
         *
         * The returned sequence is backed by an open JDBC ResultSet; iterate it within an active
         * transaction. It is closed automatically when fully iterated or when fetching the next
         * element throws; exceptions thrown by your own processing code do not close it. Unless
         * you fully iterate the sequence, close it explicitly (e.g., with [use]). It can be
         * iterated only once. See [CloseableSequence].
         *
         * When fetching a simple scalar type from a single-column query, SQL NULL is kept as a null
         * element even though this cannot be expressed in the `CloseableSequence<T>` type.
         */
        public fun <T : Any> sequence(returnType: KClass<T>): CloseableSequence<T>

        /**
         * Executes the statement (typically `INSERT`, `UPDATE`, or `DELETE`) and returns the
         * number of affected rows.
         */
        public fun rowsUpdated(): Long

        /**
         * Executes the statement and returns one row of values generated on the database side,
         * such as an auto-increment ID. The map keys and value types are those reported by the
         * configured driver and may differ from the requested column names.
         *
         * Fails with an exception if the driver returns no generated-value row or more than one
         * row.
         *
         * @param columns generated columns to request from the driver; when empty, the driver's
         * default set is requested
         */
        public fun generatedValues(vararg columns: String): Map<String, Any>
    }
}

/**
 * Executes the query and returns exactly one row converted to [T].
 *
 * A reified shortcut for [FetchSpec.single].
 */
public inline fun <reified T : Any> FetchSpec.single(): T = single(T::class)

/**
 * Executes the query and returns at most one row converted to [T], or `null` if the query
 * returns no rows.
 *
 * A reified shortcut for [FetchSpec.singleOrNull].
 */
public inline fun <reified T : Any> FetchSpec.singleOrNull(): T? = singleOrNull(T::class)

/**
 * Executes the query and returns all rows converted to [T].
 *
 * A reified shortcut for [FetchSpec.list].
 */
public inline fun <reified T : Any> FetchSpec.list(): List<T> = list(T::class)

/**
 * Executes the query and returns each row converted to [T], as a sequence that streams rows
 * without accumulating them all in memory.
 *
 * A reified shortcut for [FetchSpec.sequence]; see it for the resource-handling caveats.
 */
public inline fun <reified T : Any> FetchSpec.sequence(): CloseableSequence<T> = sequence(T::class)
