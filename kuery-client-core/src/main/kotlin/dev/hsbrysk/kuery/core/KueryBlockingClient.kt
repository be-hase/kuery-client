package dev.hsbrysk.kuery.core

import dev.hsbrysk.kuery.core.KueryBlockingClient.FetchSpec
import kotlin.reflect.KClass

public interface KueryBlockingClient {
    /**
     * Returns a [FetchSpec] to obtain the execution results based on the received [SqlBuilder].
     *
     * @param sqlId An ID that uniquely identifies the query. It is used for purposes such as metrics.
     * @param block [SqlBuilder] for constructing SQL.
     */
    public fun sql(
        sqlId: String,
        block: SqlBuilder.() -> Unit,
    ): FetchSpec

    /**
     * Returns a [FetchSpec] to obtain the execution results based on the received [SqlBuilder].
     *
     * @param block [SqlBuilder] for constructing SQL.
     */
    public fun sql(block: SqlBuilder.() -> Unit): FetchSpec

    @Suppress("TooManyFunctions")
    public interface FetchSpec {
        /**
         * Set the fetch size to use when executing this query.
         */
        public fun fetchSize(fetchSize: Int): FetchSpec

        /**
         * Set the maximum number of rows to return from this query.
         */
        public fun maxRows(maxRows: Int): FetchSpec

        /**
         * Set the query timeout (in seconds) for this query.
         */
        public fun queryTimeoutSeconds(queryTimeoutSeconds: Int): FetchSpec

        /**
         * Receives the results as a map.
         */
        public fun singleMap(): Map<String, Any?>

        /**
         * Receives the results as a map.
         */
        public fun singleMapOrNull(): Map<String, Any?>?

        /**
         * Receives the results converted to the specified type.
         */
        public fun <T : Any> single(returnType: KClass<T>): T

        /**
         * Receives the results converted to the specified type.
         */
        public fun <T : Any> singleOrNull(returnType: KClass<T>): T?

        /**
         * Receives the results of multiple rows as a map.
         */
        public fun listMap(): List<Map<String, Any?>>

        /**
         * Receives the results of multiple rows converted to the specified type.
         */
        public fun <T : Any> list(returnType: KClass<T>): List<T>

        /**
         * Receives the results of multiple rows as a sequence of maps.
         * The returned sequence is backed by an open JDBC ResultSet; iterate it within an active transaction.
         * It is closed automatically when fully iterated or when an exception is thrown during the iteration.
         * If you stop iterating midway, close it explicitly (e.g., with [use]). It can be iterated only once.
         */
        public fun sequenceMap(): CloseableSequence<Map<String, Any?>>

        /**
         * Receives the results of multiple rows converted to the specified type as a sequence.
         * The returned sequence is backed by an open JDBC ResultSet; iterate it within an active transaction.
         * It is closed automatically when fully iterated or when an exception is thrown during the iteration.
         * If you stop iterating midway, close it explicitly (e.g., with [use]). It can be iterated only once.
         */
        public fun <T : Any> sequence(returnType: KClass<T>): CloseableSequence<T>

        /**
         * Contract for fetching the number of affected rows
         */
        public fun rowsUpdated(): Long

        /**
         * Receives the values generated on the database side.
         * For example, an auto increment value.
         */
        public fun generatedValues(vararg columns: String): Map<String, Any>
    }
}

/**
 * Receives the results converted to the specified type.
 */
public inline fun <reified T : Any> FetchSpec.single(): T = single(T::class)

/**
 * Receives the results converted to the specified type.
 */
public inline fun <reified T : Any> FetchSpec.singleOrNull(): T? = singleOrNull(T::class)

/**
 * Receives the results of multiple rows converted to the specified type.
 */
public inline fun <reified T : Any> FetchSpec.list(): List<T> = list(T::class)

/**
 * Receives the results of multiple rows converted to the specified type as a sequence.
 */
public inline fun <reified T : Any> FetchSpec.sequence(): CloseableSequence<T> = sequence(T::class)
