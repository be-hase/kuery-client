---
description: Build safe static and dynamic SQL with +/add, string-interpolation parameter binding, constants, collections, and reusable Kotlin helpers.
---

# Building SQL

## SQL fragments

### `+` (`unaryPlus`)

Add SQL fragments with the unary `+` operator. Fragments are joined with newlines.

```kotlin
kueryClient
    .sql {
        +"SELECT * FROM users"
        +"WHERE user_id = 1"
    }
```

A fragment can also span multiple lines:

```kotlin
kueryClient
    .sql {
        +"""
        SELECT * FROM users
        WHERE user_id = 1
        """
    }
```

### `add(sql: String)`

`add(...)` is an alias for unary `+`. Its argument is annotated with
`org.intellij.lang.annotations.Language`, so JetBrains IDEs can provide SQL syntax assistance.

### Automatic `trimIndent` (opt-in)

With a multi-line string, the source indentation stays in the SQL body. That is harmless to the
database, but it makes logged SQL noisy, so `.trimIndent()` is commonly appended. If you would
rather not write it every time, enable `autoTrimIndent` in the Gradle plugin:

```kotlin
kueryClient {
    autoTrimIndent = true
}
```

Every string passed to `+` / `add()` then gets `trimIndent()` applied automatically. For string
literals and templates the trimming is computed **at compile time** by the compiler plugin, so it
adds no runtime cost. Arguments the plugin cannot see through (e.g. a variable) are trimmed at
runtime instead.

Notes:

- `addUnsafe()` is not affected — use it when you need to keep indentation as-is.
- An explicit `.trimIndent()` left behind is the worst of both worlds: it prevents compile-time
  trimming, so the string ends up trimmed twice at runtime. The compiler reports a
  `KUERY_REDUNDANT_TRIM_INDENT` warning for such calls — remove them when enabling the option.
  (Behavior stays correct either way; the double trim only differs when the explicitly trimmed
  string still starts or ends with a blank line, which the automatic trim then drops.)
- An explicit `.trimMargin()` is not flagged — auto-trim does not remove margins. Note however
  that the automatic trim runs on its result, so per-line indentation deliberately kept after the
  margin prefix (e.g. `|  SELECT`) is stripped; use `addUnsafe()` when such indentation must
  survive.
- The option defaults to `false`, so existing builds are unaffected.

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
template are expanded into the SQL text at compile time instead of being bound:

| Interpolated expression | Examples | Behavior |
|---|---|---|
| Runtime value | `$userId`, `${user.id}`, `${find()}` | Bound as a parameter (`:p0`) |
| `String` / `Char` constant (literal or `const val`) | `${"users"}`, `$TABLE`, `${'$'}` | Expanded into the SQL text |
| Constant of any other type (literal or `const val`) | `${1}`, `${true}`, `${null}` | Bound as a parameter |

For example, you can share a table name as a `const val`: it is expanded into the SQL text, while
the runtime value `userId` in the same template is still bound as a parameter:

```kotlin
const val TABLE = "users"

kueryClient
    .sql {
        +"SELECT * FROM $TABLE WHERE user_id = $userId"
        // SQL body: SELECT * FROM users WHERE user_id = :p0
    }
```

Sometimes the SQL itself needs a literal `$` — JSON path syntax, for example. `$` cannot be
written as-is in a Kotlin string template (and raw strings have no backslash escaping), so the
idiomatic escape is the `Char` constant `${'$'}`. Since `Char` constants are expanded as text,
the `$` simply comes out in the SQL:

```kotlin
kueryClient
    .sql {
        +"SELECT data->>'${'$'}.name' FROM articles WHERE article_id = $articleId"
        // SQL body: SELECT data->>'$.name' FROM articles WHERE article_id = :p0
    }
```

::: warning
A `String` constant intended as a *value* (e.g. `WHERE name = $NAME_CONST`) is expanded without
quoting, so the query will most likely fail with a database error. Since it is a compile-time
constant this cannot cause SQL injection, but if you want it bound, use a non-const `val`.
:::

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

::: warning Empty collections
Spring expands an empty collection to `IN ()`. H2 accepts that syntax, but MySQL and PostgreSQL reject it.
Return early or build a different predicate when the collection is empty. See
[Supported Platforms](/supported-platforms#empty-collections-in-in-clauses).
:::

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

Primitive arrays are not treated as object arrays. For example, `ByteArray` is passed to the driver as one binary
value rather than expanded into multiple parameters.

### Enums

An enum value is bound by its name (`Enum.name`) by default. This also applies to enums inside collections and
arrays.

```kotlin
val status = UserStatus.ACTIVE
kueryClient
    .sql {
        +"SELECT * FROM users WHERE status = $status"
        // bound as the string 'ACTIVE'
    }
```

If you want a different representation, register a custom `@WritingConverter` — it takes precedence over the
default. See [Type Conversion](/type-conversion).

### Null values

A `null` value is bound as SQL `NULL`. Be careful with comparison operators: `column = NULL` never matches
anything in SQL. If a value can be null, branch explicitly:

```kotlin
kueryClient
    .sql {
        +"SELECT * FROM users"
        if (email != null) {
            +"WHERE email = $email"
        } else {
            +"WHERE email IS NULL"
        }
    }
```

## Dynamic SQL with Kotlin control flow

Use normal Kotlin `if`, `when`, loops, and function calls. There is no separate template language.

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

## Reusable query parts

Query parts shared across queries can be extracted into plain extension functions on `SqlBuilder`. String
interpolation inside them is converted into bind parameters as usual, and Kotlin control flow works as usual —
no special API is needed:

```kotlin
fun SqlBuilder.whereActiveUsers(tenantId: Int, username: String? = null) {
    +"WHERE tenant_id = $tenantId"
    +"AND status = 'ACTIVE'"
    if (username != null) {
        +"AND username = $username"
    }
}

class UserRepository(private val kueryClient: KueryClient) {
    suspend fun search(tenantId: Int, username: String?): List<User> = kueryClient
        .sql {
            +"SELECT * FROM users"
            whereActiveUsers(tenantId, username)
            +"ORDER BY username"
        }
        .list()

    suspend fun count(tenantId: Int): Long = kueryClient
        .sql {
            +"SELECT COUNT(*) FROM users"
            whereActiveUsers(tenantId)
        }
        .single()
}
```

For fragments that must be assembled as a string dynamically (e.g. a variable number of placeholders), see
[Helpers](/helpers).

## Fetch Result

`sql { ... }` returns a `FetchSpec`; a terminal operation such as `single()`, `list()`, `flow()`, `sequence()`,
or `rowsUpdated()` executes it. Continue to [Fetching Results](/fetching-results) for row-count rules, execution
timing, JDBC resource management, generated-key portability, and statement options.
