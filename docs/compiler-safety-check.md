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
`KUERY_UNSAFE_SQL_STRING` **warning** when the argument of `add()` / `+` cannot be fully analyzed
at compile time. Typical flagged arguments:

| Flagged argument | Problem |
|---|---|
| `add(sql)` — a `String` variable or parameter | The string may already contain concatenated runtime values |
| `add("... WHERE " + cond)` — `+` concatenation | Not a string template; the plugin cannot rewrite it into bind parameters |
| `add(buildWhere())` — a function call | The plugin cannot see what the function returns |
| `add("WHERE aaa".replace("aaa", input))` — a chained call on a literal | Same as above — and a runtime argument like `input` ends up in the SQL text without being bound |
| `add(run { "SELECT 1" })` — a lambda / scope function | The plugin does not look inside lambdas, even when the body is just a literal |
| `add(if (id > 0) "SELECT 1" else fragment)` — an `if` with an unsafe branch | Every branch must be safe on its own; `fragment` is a variable |

For example, the most common case — passing a variable — is reported like this:

```kotlin
val sql = "SELECT * FROM users WHERE user_id = $userId"
kueryClient.sql {
    add(sql) // KUERY_UNSAFE_SQL_STRING: `$userId` was already concatenated; executed as raw SQL
}
```

In contrast, the following forms are accepted. The SQL string is fully determined at compile
time, so any interpolation in it is rewritten into bind parameters:

- A string literal / string template: `"SELECT ... $id"`
- `trimIndent()` / `trimMargin()` called on one (with literal-only arguments) — these are also
  computed at compile time by the plugin, so they add no runtime cost (in the rare cases where
  equivalence cannot be guaranteed, e.g. a `trimMargin` prefix containing `:`, the plugin leaves
  the call as-is and it runs at runtime as before)
- A reference to a `const val`
- An `if` / `when` expression whose every branch is one of the above (each branch is checked
  recursively): `if (asc) "ORDER BY id" else "ORDER BY id DESC"`. A branch that only throws —
  e.g. `else -> error("unsupported sort")` — is also fine, since it never produces a SQL string.

## Responding to the warning

In order of preference:

1. **Restructure into safe forms.** Most dynamic SQL can be expressed with Kotlin control flow
   inside the `sql { }` block — `if` / `when` / loops around `+"..."` — which the plugin handles
   safely. See [Basics](/basics#logic-such-as-if-and-for-etc).
2. **Suppress the warning** with `@Suppress("KUERY_UNSAFE_SQL_STRING")` on the declaration, when
   you know the string is safe but the checker cannot see it.

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
