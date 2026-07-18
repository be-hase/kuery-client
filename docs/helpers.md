---
description: Built-in values helper for multi-row inserts and guidance on writing custom SqlBuilder extension helpers using addUnsafe / bind.
---

# Helpers

## `values`

Use `values` to build a multi-row insert while binding every value:

```kotlin
data class UserParam(val username: String, val email: String?, val age: Int)

suspend fun insertMany(params: List<UserParam>): Long = kueryClient
    .sql {
        +"INSERT INTO users (username, email, age)"
        values(params) { listOf(it.username, it.email, it.age) }
    }
    .rowsUpdated()

// INSERT INTO users (username, email, age) VALUES (:p0, :p1, :p2), (:p3, :p4, :p5), ...
```

## Writing a custom helper

The built-in `values` function uses the same lower-level API available to custom helpers:

```kotlin
@OptIn(DelicateKueryClientApi::class)
fun SqlBuilder.values(input: List<List<Any?>>) {
    require(input.isNotEmpty()) { "input must not be empty" }
    val firstSize = input.first().size
    require(input.all { it.size == firstSize }) { "all rows must have the same size" }
    require(firstSize > 0) { "rows must not be empty" }

    val placeholders = input.joinToString(", ") { list ->
        list.joinToString(separator = ", ", prefix = "(", postfix = ")") {
            bind(it)
        }
    }
    addUnsafe("VALUES $placeholders")
}

fun <T> SqlBuilder.values(
    input: List<T>,
    transformer: (T) -> List<Any?>,
) {
    values(input.map { transformer(it) })
}
```

Some SQL fragments cannot be expressed with plain string interpolation — for example, a dynamically sized list
of placeholders. In such cases, use `addUnsafe` and `bind` (the `values` function above is a good example).

`bind` only makes sense together with `addUnsafe`. Calling it inside a string template passed to `add()` / `+`
is a compile error (`KUERY_BIND_CALL_IN_SQL_TEMPLATE`) — see
[Compile-Time Checks](/compiler-safety-check#bind-in-a-string-template) for why.

`addUnsafe` and `bind` are annotated with `@DelicateKueryClientApi` and require an explicit opt-in because
untrusted text passed to `addUnsafe` can cause SQL injection. Keep all runtime values in `bind(...)`; use
`addUnsafe(...)` only for SQL syntax assembled by trusted application code. See
[Compile-Time Checks](/compiler-safety-check) for details.

The built-in `values` overloads reject an empty input, an empty row, and rows with different sizes by throwing
`IllegalArgumentException`.
