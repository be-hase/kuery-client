package dev.hsbrysk.kuery.sqlx4k

import dev.hsbrysk.kuery.core.Sql
import dev.hsbrysk.kuery.core.SqlBuilder
import io.github.smyrgeorge.sqlx4k.QueryExecutor
import io.github.smyrgeorge.sqlx4k.ResultSet
import io.github.smyrgeorge.sqlx4k.Statement

/**
 * PoC: minimal KueryClient-like facade backed by a sqlx4k [QueryExecutor].
 *
 * Deliberate subset compared to the Spring-backed clients:
 * - no Micrometer observation (decided out of scope for the sqlx4k backend)
 * - no `flow()` / `generatedValues()` — sqlx4k has neither row streaming nor a
 *   generated-keys API
 * - row mapping is an explicit `(ResultSet.Row) -> T` function; Kotlin/Native has no
 *   kotlin-reflect, so the reflection-based data-class mapping of the Spring backends
 *   cannot exist here
 */
public class Sqlx4kKueryClient(private val executor: QueryExecutor) {
    public fun sql(block: SqlBuilder.() -> Unit): Sqlx4kFetchSpec = Sqlx4kFetchSpec(executor, Sql(block))
}

public class Sqlx4kFetchSpec internal constructor(
    private val executor: QueryExecutor,
    private val sql: Sql,
) {
    // single/singleOrNull decide on the ROW count before mapping — a mapper may legitimately
    // return null (e.g. a nullable column), which must not be confused with "no rows".
    public suspend fun <T> single(mapper: (ResultSet.Row) -> T): T {
        val rows = rows()
        if (rows.isEmpty()) throw NoSuchElementException("Expected exactly one row, but none matched.")
        check(rows.size == 1) { "Expected exactly one row, but ${rows.size} rows matched." }
        return mapper(rows.single())
    }

    public suspend fun <T> singleOrNull(mapper: (ResultSet.Row) -> T): T? {
        val rows = rows()
        check(rows.size <= 1) { "Expected at most one row, but ${rows.size} rows matched." }
        return rows.firstOrNull()?.let(mapper)
    }

    public suspend fun <T> list(mapper: (ResultSet.Row) -> T): List<T> = rows().map(mapper)

    private suspend fun rows(): List<ResultSet.Row> = executor.fetchAll(statement()).getOrThrow().toList()

    public suspend fun rowsUpdated(): Long = executor.execute(statement()).getOrThrow()

    /**
     * Bridges the core abstraction to sqlx4k: [Sql.body] already contains `:pN` placeholders,
     * which is exactly sqlx4k's named-parameter syntax, so the conversion is a plain rebind.
     */
    private fun statement(): Statement {
        var statement = Statement.create(sql.body)
        for (parameter in sql.parameters) {
            statement = statement.bind(parameter.name, parameter.value)
        }
        return statement
    }
}
