---
description: "SQL builder API: +/add, parameter binding via string interpolation, Kotlin control flow, and the fetch API (single, list, flow, sequence, rowsUpdated, generatedValues, fetchSize, maxRows, queryTimeoutSeconds)."
---

# Basics

## Building SQL

### `+`(unaryPlus)

Concatenate SQL strings using the + operator.

```kotlin
kueryClient
    .sql {
        +"SELECT * FROM users"
        +"WHERE user_id = 1"
    }
```

Of course, if there is no need to concatenate, you don't have to.

```kotlin
kueryClient
    .sql {
        +"""
        SELECT * FROM users
        WHERE user_id = 1
        """
    }
```

### `fun add(sql: String)`

It is an alias for `+`(unaryPlus). However, since the argument is annotated
with `org.intellij.lang.annotations.Language`, if you are using a JetBrains IDE, you will get syntax assistance.

## Binding Parameters

When you want to bind parameters, use string interpolation.

```kotlin
val userId = "..."
kueryClient
    .sql {
        +"""
        SELECT * FROM users
        WHERE user_id = $userId
        """
    }
```

### Compile-time constants are expanded as text

Only runtime values are bound as parameters. Compile-time `String` / `Char` constants inside a
template — string literals like `${"users"}`, references to a `const val`, and `Char` constants
like `${'$'}` — are expanded into the SQL text at compile time instead of being bound.

```kotlin
const val TABLE = "users"

kueryClient
    .sql {
        +"SELECT * FROM $TABLE WHERE user_id = $userId"
        // SQL body: SELECT * FROM users WHERE user_id = :p0
    }
```

This also means a literal `$` comes out right. `$` cannot be written as-is in a Kotlin string
template (and raw strings have no backslash escaping), so the idiomatic escape is the `Char`
constant `${'$'}` — which is simply expanded back into the SQL text:

```kotlin
kueryClient
    .sql {
        +"SELECT data->>'${'$'}.name' FROM articles WHERE article_id = $articleId"
        // SQL body: SELECT data->>'$.name' FROM articles WHERE article_id = :p0
    }
```

Constants of other types (`${1}`, `${true}`, `${null}`, ...) are still bound as parameters —
only `String` and `Char` constants are expanded as text.

Note that a `String` constant intended as a *value* (e.g. `WHERE name = $NAME_CONST`) is expanded
without quoting, so the query will most likely fail with a database error. Since it is a
compile-time constant this cannot cause SQL injection, but if you want it bound, use a non-const
`val`.

### Collections and arrays

A `Collection` is bound as a single named parameter that Spring expands into the individual
elements — this is what you want for `IN` clauses:

```kotlin
val statuses = listOf(UserStatus.ACTIVE, UserStatus.INACTIVE)
kueryClient
    .sql {
        +"SELECT * FROM users WHERE status IN ($statuses)"
    }
```

An array is different: it is passed to the driver as a single array value with its element type
preserved. It is *not* expanded for `IN`. Use arrays with databases that support them natively,
such as PostgreSQL (`= ANY(...)`, array columns):

```kotlin
val usernames = arrayOf("user1", "user2")
kueryClient
    .sql {
        +"SELECT * FROM users WHERE username = ANY($usernames)"
    }
```

MySQL has no array type, so use a `Collection` for `IN` clauses there.

## Logic such as `if` and `for` ...etc

Just write using Kotlin syntax. There is no need to learn special syntax.

```kotlin
kueryClient
    .sql {
        +"SELECT * FROM users"
        +"WHERE"
        +"status = $status"
        if (vip != null) {
            +"AND vip = $vip"
        }
    }
```

## Fetch Result

`kuery-client-spring-data-r2dbc/jdbc` both have a minimal interface. In the case of `kuery-client-spring-data-r2dbc`, it
will be a suspend function.

### (suspend) fun singleMap(): Map<String, Any?>

Receives the results as a map.

```kotlin
val map: Map<String, Any?> = kueyClient
    .sql { +"SELECT * FROM users WHERE user_id = 1" }
    .singleMap()
```

### `(suspend) fun singleMapOrNull(): Map<String, Any?>?`

Receives the results as a map.

```kotlin
val map: Map<String, Any?>? = kueyClient
    .sql { +"SELECT * FROM users WHERE user_id = 1" }
    .singleMapOrNull()
```

### `(suspend) fun <T : Any> single(returnType: KClass<T>): T`

Receives the results converted to the specified type.

```kotlin
val user: User = kueyClient
    .sql { +"SELECT * FROM users WHERE user_id = 1" }
    .single()
```

### `(suspend) fun <T : Any> singleOrNull(returnType: KClass<T>): T?`

Receives the results converted to the specified type.

```kotlin
val user: User? = kueyClient
    .sql { +"SELECT * FROM users WHERE user_id = 1" }
    .singleOrNull()
```

### `(suspend) fun listMap(): List<Map<String, Any?>>`

Receives the results of multiple rows as a map.

```kotlin
val result: List<Map<String, Any?>> = kueyClient
    .sql { +"SELECT * FROM users WHERE user_id = 1" }
    .listMap()
```

### `(suspend) fun <T : Any> list(returnType: KClass<T>): List<T>`

Receives the results of multiple rows converted to the specified type.

```kotlin
val users: List<User> = kueyClient
    .sql { +"SELECT * FROM users WHERE user_id = 1" }
    .list()
```

### [`kuery-client-spring-data-r2dbc` only] `fun flowMap(): Flow<Map<String, Any?>>`

Receives the results of multiple rows as a map.

```kotlin
val result: Flow<Map<String, Any?>> = kueyClient
    .sql { +"SELECT * FROM users WHERE user_id = 1" }
    .flowMap()
```

### [`kuery-client-spring-data-r2dbc` only] `fun <T : Any> flow(returnType: KClass<T>): Flow<T>`

Receives the results of multiple rows converted to the specified type.

```kotlin
val users: Flow<User> = kueyClient
    .sql { +"SELECT * FROM users WHERE user_id = 1" }
    .flow()
```

### [`kuery-client-spring-data-jdbc` only] `fun sequenceMap(): Sequence<Map<String, Any?>>`

Receives the results of multiple rows as a sequence of maps.

```kotlin
val result: Sequence<Map<String, Any?>> = kueyClient
    .sql { +"SELECT * FROM users WHERE user_id = 1" }
    .sequenceMap()
```

Note: backed by an open JDBC ResultSet — iterate within an active transaction. Single-pass.

### [`kuery-client-spring-data-jdbc` only] `fun <T : Any> sequence(returnType: KClass<T>): Sequence<T>`

Receives the results of multiple rows converted to the specified type as a sequence.

```kotlin
val users: Sequence<User> = kueyClient
    .sql { +"SELECT * FROM users WHERE user_id = 1" }
    .sequence()
```

Note: backed by an open JDBC ResultSet — iterate within an active transaction. Single-pass.

### `(suspend) fun rowsUpdated(): Long`

Contract for fetching the number of affected rows

```kotlin
val result: Long = kueyClient
    .sql {+"INSERT INTO users (username, email) VALUES ('username1', 'email1')"}
    .rowsUpdated()
```

### `(suspend) fun generatedValues(vararg columns: String): Map<String, Any>`

Receives the values generated on the database side. For example, an auto increment value.

```kotlin
val result: Map<String, Any> = kueyClient
    .sql {+"INSERT INTO users (username, email) VALUES ('username1', 'email1')"}
    .generatedValues("user_id")
```

### `fun fetchSize(fetchSize: Int): FetchSpec`

Apply the given fetch size to any subsequent query statement.

Available for both `kuery-client-spring-data-r2dbc` and `kuery-client-spring-data-jdbc`.

```kotlin
val users: List<User> = kueyClient
    .sql { +"SELECT * FROM users" }
    .fetchSize(100)
    .list()
```

### [`kuery-client-spring-data-jdbc` only] `fun maxRows(maxRows: Int): FetchSpec`

Apply the given maximum number of rows to any subsequent query statement.

```kotlin
val users: List<User> = kueyClient
    .sql { +"SELECT * FROM users" }
    .maxRows(1000)
    .list()
```

### [`kuery-client-spring-data-jdbc` only] `fun queryTimeoutSeconds(queryTimeout: Int): FetchSpec`

Set the query timeout (in seconds) for this query.

```kotlin
val users: List<User> = kueyClient
    .sql { +"SELECT * FROM users" }
    .queryTimeoutSeconds(30)
    .list()
```
