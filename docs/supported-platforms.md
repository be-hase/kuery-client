---
description: Databases, drivers, and JVM platforms exercised by Kuery Client's automated test suite, plus database-specific behavior to account for.
---

# Supported Platforms

Kuery Client delegates database access to Spring Data and the configured JDBC/R2DBC driver. It may work with
other Spring-supported databases, but the combinations below are the ones exercised by this repository's
automated tests.

## Continuously tested databases

| Database | JDBC driver | R2DBC driver | Test environment |
|---|---|---|---|
| MySQL | `com.mysql:mysql-connector-j` | `io.asyncer:r2dbc-mysql` | MySQL 8.0.37 Testcontainer |
| PostgreSQL | `org.postgresql:postgresql` | `org.postgresql:r2dbc-postgresql` | PostgreSQL 16 Testcontainer |
| H2 | `com.h2database:h2` | `io.r2dbc:r2dbc-h2` | In-memory database |

The suite runs the shared client behavior against both JDBC and R2DBC. Driver-dependent features—including
parameter binding, arrays, generated values, and database error handling—also run against real MySQL and
PostgreSQL containers.

::: info What “tested” means
The table describes the project's automated test matrix, not an exclusive allow-list. A database supported by
Spring Data may work, but behavior outside this matrix is not continuously verified by this project.
:::

For compatible Java, Gradle, Kotlin, Spring Boot, and Spring Data versions, see [Compatibility](/compatibility).
The compiler plugin targets JVM and Android/JVM compilations; JavaScript, Native, and Wasm compilations are not
supported.

## Database-dependent behavior

### Empty collections in `IN` clauses

Spring expands an empty collection to `IN ()`. H2 accepts that syntax and returns no rows, while MySQL and
PostgreSQL reject it as invalid SQL. Guard the empty case before building the query:

```kotlin
suspend fun findByIds(ids: List<Long>): List<User> {
    if (ids.isEmpty()) return emptyList()

    return kueryClient
        .sql { +"SELECT * FROM users WHERE user_id IN ($ids)" }
        .list()
}
```

The same rule applies to the blocking client without `suspend`.

### SQL arrays

An object array such as `Array<String>` is bound as one driver array value; it is not expanded as an `IN` list.
This is useful with PostgreSQL array columns and `= ANY(...)`. MySQL has no equivalent native array type, so use
a non-empty `Collection` for an `IN` clause instead. Primitive arrays such as `ByteArray` remain a single binary
value.

### Generated values

Generated-key labels and JVM value types come from the driver. For example, the tested MySQL R2DBC driver returns
the requested `user_id` key as a `Long`, while MySQL Connector/J reports `GENERATED_KEY` as a `BigInteger` even
when `"user_id"` was requested.

When only one value is requested and its label is not important, use the single map value and convert it to the
application type:

```kotlin
val generated = kueryClient
    .sql { +"INSERT INTO users (username) VALUES ($username)" }
    .generatedValues("user_id")

val userId = (generated.values.single() as Number).toLong()
```

`generatedValues(...)` expects exactly one generated-key row. It throws when no keys are produced or when a
statement produces multiple key rows. The exact keys available are database- and driver-dependent.

### SQL syntax dialects

The dialect selected by [`sqlSyntaxCheck`](/sql-syntax-check#choosing-a-dialect) controls compile-time parser
validation only. It does not configure the runtime driver and is not a declaration that every feature of that
database has been tested.
