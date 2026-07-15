---
description: The KUERY_UNSAFE_SQL_STRING compiler warning that catches SQL strings the plugin cannot convert into bind parameters.
---

# Compiler Safety Check

The kuery-client compiler plugin converts string interpolation inside
`SqlBuilder.add()` / `+"..."` into named bind parameters. This conversion
only works when the SQL string is written **directly as a string
literal/template at the call site**. If you pass anything else — for
example a variable — the plugin cannot see the interpolation, and the
string would be executed as raw SQL.

To prevent this from happening silently, the compiler plugin reports a
`KUERY_UNSAFE_SQL_STRING` **warning** when the argument of `add()` /
`+` is not one of the following:

| Argument form | Why it is safe |
|---|---|
| A string literal / string template (`"... $id"`) | Interpolation is converted into bind parameters |
| `trimIndent()` / `trimMargin()` called on the above (with literal-only arguments) | Pure formatting of a compile-time string |
| A reference to a `const val` | Compile-time constant; cannot contain runtime values |
| An `if` / `when` expression whose every branch is one of the above (`if (asc) "ORDER BY id" else "ORDER BY id DESC"`) | Each branch is checked recursively |

```kotlin
val sql = "SELECT * FROM users WHERE user_id = $userId"
kueryClient.sql {
    add(sql) // KUERY_UNSAFE_SQL_STRING: `$userId` was already concatenated; executed as raw SQL
}
```

## When you really need dynamic SQL

Use `addUnsafe()` together with `bind()`. These APIs require opting in to
`@DelicateKueryClientApi` and make the intent explicit. See [Helpers](/helpers)
for a complete example.

```kotlin
@OptIn(DelicateKueryClientApi::class)
fun SqlBuilder.userIdIn(userIds: List<Int>) {
    addUnsafe("user_id IN (${userIds.joinToString(",") { bind(it) }})")
}
```

Alternatively, suppress the warning per declaration when you know the
string is safe:

```kotlin
@Suppress("KUERY_UNSAFE_SQL_STRING")
fun query(sql: String) = kueryClient.sql { add(sql) }
```

## Configuring the severity

The check is a regular Kotlin compiler diagnostic, so the standard
mechanisms apply:

- Treat it as an error (recommended for security-sensitive projects):
  `-Xwarning-level=KUERY_UNSAFE_SQL_STRING:error`, or enable
  `allWarningsAsErrors` (`-Werror`)
- Disable it project-wide (not recommended):
  `-Xwarning-level=KUERY_UNSAFE_SQL_STRING:disabled`

In Gradle:

```kotlin
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xwarning-level=KUERY_UNSAFE_SQL_STRING:error")
    }
}
```
