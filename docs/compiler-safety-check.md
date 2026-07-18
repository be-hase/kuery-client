---
description: The KUERY_UNSAFE_SQL_STRING compiler warning that catches SQL strings the plugin cannot convert into bind parameters, and the strict option that turns the safety warnings into compile errors.
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
- `trimIndent()` / `trimMargin()` called on one (with literal-only arguments)
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

## Strict mode

Enable `strict` in the Gradle plugin to report the SQL-safety diagnostics as compile
**errors** instead of warnings (recommended for security-sensitive projects):

```kotlin
kueryClient {
    strict = true
}
```

This makes the compiler plugin register `KUERY_UNSAFE_SQL_STRING` — and, when the
[SQL Syntax Check](/sql-syntax-check) is enabled, `KUERY_SQL_SYNTAX` / `KUERY_SQL_DIALECT` —
as **error**-severity diagnostics. `@Suppress` on the enclosing declaration keeps working for
individual call sites, and `addUnsafe()` + `bind()` remain the sanctioned way to build SQL
dynamically. `KUERY_REDUNDANT_TRIM_INDENT` stays a warning — a style issue, not a safety issue.

The escalated set may grow in minor releases as new safety diagnostics are added — enabling
strict mode opts into those too.

An explicit `-Xwarning-level` for one of these diagnostics always wins over strict mode: strict
only changes the **default** severity of the diagnostics you have not configured yourself. So a
single diagnostic can still be lowered back to a warning (or disabled) module-wide with strict
enabled:

```kotlin
kotlin {
    compilerOptions {
        // keep everything else strict, but let the dialect check stay advisory
        freeCompilerArgs.add("-Xwarning-level=KUERY_SQL_DIALECT:warning")
    }
}
```

## Configuring the severity manually

The checks are regular Kotlin compiler diagnostics, so `-Xwarning-level` works as usual —
without strict mode to escalate a single diagnostic, with strict mode to lower one back (see
above), or to disable one project-wide (not recommended):

```kotlin
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xwarning-level=KUERY_UNSAFE_SQL_STRING:error")
    }
}
```

`allWarningsAsErrors` (`-Werror`) likewise turns all warnings into errors.

## See also

The compiler plugin can also validate the SQL text itself at compile time — see the opt-in
[SQL Syntax Check](/sql-syntax-check).
