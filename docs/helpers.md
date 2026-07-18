---
description: Use the built-in values helper to create multi-row INSERT statements with every value safely bound.
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

The built-in `values` overloads reject an empty input, an empty row, and rows with different sizes by throwing
`IllegalArgumentException`.

Internally, `values` builds the required placeholders with the lower-level `addUnsafe()` / `bind()` APIs. See
[`addUnsafe()` and `bind()`](/basics#addunsafe-and-bind) when writing a different kind of custom SQL fragment.
