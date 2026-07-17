package dev.hsbrysk.kuery.core

import dev.hsbrysk.kuery.core.KueryClient.FetchSpec
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * A coroutine-based SQL client.
 *
 * SQL is written with plain Kotlin string interpolation inside the [sql] block; the Kuery Client
 * compiler plugin rewrites every interpolated value into a named bind parameter, so values are
 * never concatenated into the SQL text.
 *
 * ```kotlin
 * val user: User = client.sql { +"SELECT * FROM users WHERE user_id = $userId" }.single()
 * ```
 *
 * Implementations are expected to be thread-safe; a single instance can be shared across the
 * whole application.
 *
 * For a blocking (JDBC) counterpart, see [KueryBlockingClient].
 */
public interface KueryClient {
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
     * auto sqlId generation is disabled or when the call site was not compiled with the
     * compiler plugin.
     *
     * @param block [SqlBuilder] block that constructs the SQL
     * @return a [FetchSpec] for retrieving the execution results
     */
    public fun sql(block: SqlBuilder.() -> Unit): FetchSpec

    /**
     * Specifies how to execute the built SQL and retrieve the results.
     *
     * Each terminal operation ([singleMap], [single], [list], [flow], [rowsUpdated],
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
         * Executes the query and returns exactly one row as a map keyed by column name.
         *
         * Fails with an exception if the query returns no rows or more than one row.
         */
        public suspend fun singleMap(): Map<String, Any?>

        /**
         * Executes the query and returns at most one row as a map keyed by column name, or
         * `null` if the query returns no rows.
         *
         * Fails with an exception if the query returns more than one row.
         */
        public suspend fun singleMapOrNull(): Map<String, Any?>?

        /**
         * Executes the query and returns exactly one row converted to [returnType].
         *
         * Fails with an exception if the query returns no rows, more than one row, or a single
         * scalar value that is SQL NULL.
         */
        public suspend fun <T : Any> single(returnType: KClass<T>): T

        /**
         * Executes the query and returns at most one row converted to [returnType], or `null`
         * if the query returns no rows.
         *
         * Fails with an exception if the query returns more than one row.
         */
        public suspend fun <T : Any> singleOrNull(returnType: KClass<T>): T?

        /**
         * Executes the query and returns all rows as a list of maps keyed by column name.
         */
        public suspend fun listMap(): List<Map<String, Any?>>

        /**
         * Executes the query and returns all rows converted to [returnType].
         *
         * When fetching a simple scalar type from a single-column query, SQL NULL is kept as a null
         * element even though this cannot be expressed in the `List<T>` type.
         */
        public suspend fun <T : Any> list(returnType: KClass<T>): List<T>

        /**
         * Executes the query and emits each row as a map keyed by column name, without
         * accumulating all rows in memory.
         *
         * The query is executed when the returned [Flow] is collected.
         */
        public fun flowMap(): Flow<Map<String, Any?>>

        /**
         * Executes the query and emits each row converted to [returnType], without accumulating
         * all rows in memory.
         *
         * The query is executed when the returned [Flow] is collected.
         *
         * When fetching a simple scalar type from a single-column query, SQL NULL is kept as a null
         * element even though this cannot be expressed in the `Flow<T>` type.
         */
        public fun <T : Any> flow(returnType: KClass<T>): Flow<T>

        /**
         * Executes the statement (typically `INSERT`, `UPDATE`, or `DELETE`) and returns the
         * number of affected rows.
         */
        public suspend fun rowsUpdated(): Long

        /**
         * Executes the statement and returns the values generated on the database side, such as
         * an auto-increment ID, as a map keyed by column name.
         *
         * @param columns the names of the generated columns to return; when empty, the
         * driver's default set of generated values is returned
         */
        public suspend fun generatedValues(vararg columns: String): Map<String, Any>
    }
}

/**
 * Executes the query and returns exactly one row converted to [T].
 *
 * A reified shortcut for [FetchSpec.single].
 */
public suspend inline fun <reified T : Any> FetchSpec.single(): T = single(T::class)

/**
 * Executes the query and returns at most one row converted to [T], or `null` if the query
 * returns no rows.
 *
 * A reified shortcut for [FetchSpec.singleOrNull].
 */
public suspend inline fun <reified T : Any> FetchSpec.singleOrNull(): T? = singleOrNull(T::class)

/**
 * Executes the query and returns all rows converted to [T].
 *
 * A reified shortcut for [FetchSpec.list].
 */
public suspend inline fun <reified T : Any> FetchSpec.list(): List<T> = list(T::class)

/**
 * Executes the query and emits each row converted to [T], without accumulating all rows in memory.
 *
 * A reified shortcut for [FetchSpec.flow].
 */
public inline fun <reified T : Any> FetchSpec.flow(): Flow<T> = flow(T::class)
