---
description: Execute queries and map results with single, list, flow, sequence, rowsUpdated, generatedValues, and statement options.
---

# Fetching Results

`sql { ... }` builds a `FetchSpec`; it does not execute the statement. A terminal operation executes the SQL and
returns its result. R2DBC terminal operations are suspending except for `flow()` / `flowMap()`. JDBC operations
are blocking.

## Operation summary

| Method | Result | R2DBC | JDBC |
|---|---|:---:|:---:|
| `single()` / `singleMap()` | Exactly one row | ✓ | ✓ |
| `singleOrNull()` / `singleMapOrNull()` | Zero or one row | ✓ | ✓ |
| `list()` / `listMap()` | All rows in memory | ✓ | ✓ |
| `flow()` / `flowMap()` | Coroutine `Flow` | ✓ | — |
| `sequence()` / `sequenceMap()` | `CloseableSequence` over a JDBC result set | — | ✓ |
| `rowsUpdated()` | Affected-row count | ✓ | ✓ |
| `generatedValues(vararg columns)` | One row of generated values | ✓ | ✓ |

Typed methods use [Row Mapping](/row-mapping). `*Map` methods return
`Map<String, Any?>` values keyed by column label.

## Exactly one or at most one row

`single()` expects exactly one row. It throws when no rows or multiple rows are returned. For a simple scalar
result, it also throws when the selected value is SQL `NULL`.

`singleOrNull()` returns `null` when there is no row. For a simple scalar result, SQL `NULL` is also returned as
Kotlin `null`, so those two cases cannot be distinguished with this operation. Both methods throw for multiple
rows.

```kotlin
val user: User = kueryClient
    .sql { +"SELECT * FROM users WHERE user_id = $userId" }
    .single()

val userOrNull: User? = kueryClient
    .sql { +"SELECT * FROM users WHERE user_id = $userId" }
    .singleOrNull()
```

The `singleMap()` and `singleMapOrNull()` variants have the same row-count rules without typed conversion.

## Materializing all rows

`list()` loads all rows into memory:

```kotlin
val users: List<User> = kueryClient
    .sql { +"SELECT * FROM users WHERE status = $status" }
    .list()
```

When a nullable SQL column is fetched as a simple scalar, `list()` preserves `NULL` elements. Because the public
signature is `List<T>` rather than `List<T?>`, prefer selecting a non-null expression or use `listMap()` when
nulls are expected.

## Streaming with R2DBC

`flow()` is cold: the query executes when the returned flow is collected, and collecting the same flow again
executes the query again.

```kotlin
val users: Flow<User> = kueryClient
    .sql { +"SELECT * FROM users ORDER BY user_id" }
    .flow()

users.collect { user -> consume(user) }
```

`flow()` / `flowMap()` currently do not create a Kuery Client observation. See
[Streaming operations are not observed](/observation#streaming-operations-are-not-observed).

## Streaming with JDBC

`sequence()` executes the statement immediately when the sequence is created. Row consumption is deferred, but
the sequence is backed by an open JDBC `ResultSet` and can be iterated only once.

Fully iterating it closes the resource. If iteration may stop early—or user code may throw—use `use`:

```kotlin
kueryClient
    .sql { +"SELECT * FROM users ORDER BY user_id" }
    .sequence<User>()
    .use { users ->
        users.take(10).forEach(::consume)
    }
```

Keep iteration inside the active transaction. `sequence()` / `sequenceMap()` currently do not create a Kuery
Client observation.

## Raw maps and column labels

Use `singleMap()`, `listMap()`, `flowMap()`, or `sequenceMap()` when typed mapping is unnecessary.

```kotlin
val rows: List<Map<String, Any?>> = kueryClient
    .sql { +"SELECT user_id, username FROM users" }
    .listMap()
```

Always alias duplicate labels in joins. With duplicate labels, the JDBC mapper keeps the first value while the
R2DBC mapper keeps the last, so relying on either result is not portable:

```sql
SELECT users.id AS user_id, orders.id AS order_id
FROM users JOIN orders ON ...
```

## Updating rows

`rowsUpdated()` returns the number of affected rows as a `Long`:

```kotlin
val count: Long = kueryClient
    .sql { +"UPDATE users SET status = $status WHERE user_id = $userId" }
    .rowsUpdated()
```

## Generated values

`generatedValues(...)` returns exactly one generated-key row. The column names and JVM value types in the map
are driver-dependent, even when column names are requested.

```kotlin
val generated: Map<String, Any> = kueryClient
    .sql { +"INSERT INTO users (username, email) VALUES ($username, $email)" }
    .generatedValues("user_id")

val userId = (generated.values.single() as Number).toLong()
```

It throws an `EmptyResultDataAccessException` when no generated values are returned and an
`IncorrectResultSizeDataAccessException` when multiple generated-key rows are returned. See
[Supported Platforms](/supported-platforms#generated-values) for tested driver differences.

## Statement options

Statement options return a new `FetchSpec`; the original remains unchanged.

| Method | R2DBC | JDBC | Notes |
|---|:---:|:---:|---|
| `fetchSize(Int)` | ✓ | ✓ | Driver fetch-size hint |
| `maxRows(Int)` | — | ✓ | Maximum rows returned |
| `queryTimeoutSeconds(Int)` | — | ✓ | Query timeout in seconds |

```kotlin
val users: List<User> = kueryClient
    .sql { +"SELECT * FROM users" }
    .fetchSize(100)
    .list()
```

The driver ultimately defines the effect of fetch size and timeout settings.
