---
description: All compile-time diagnostics reported by the kuery-client compiler plugin — unsafe SQL strings, bind() misuse, SQL syntax validation, redundant trimIndent — and the strict option that escalates the safety warnings into compile errors.
---

# Compile-Time Checks

The kuery-client compiler plugin does more than rewrite interpolated runtime values into bind
parameters — it also **checks your SQL code at compile time**. This page is the overview of
every diagnostic the plugin can report:

| Diagnostic | Fires when | Severity | Enabled |
|---|---|---|---|
| [`KUERY_UNSAFE_SQL_STRING`](#unsafe-sql-strings) | `add()` / `+` receives a string the plugin cannot analyze | warning ([`strict`](#strict-mode) → error) | always |
| [`KUERY_BIND_CALL_IN_SQL_TEMPLATE`](#bind-in-a-string-template) | `bind()` is called inside a string template | always error | always |
| [`KUERY_SQL_SYNTAX`](/sql-syntax-check) | Statically-known SQL fails to parse | warning ([`strict`](#strict-mode) → error) | [`sqlSyntaxCheck`](/sql-syntax-check) |
| [`KUERY_SQL_DIALECT`](/sql-syntax-check#choosing-a-dialect) | SQL uses a feature the dialect lacks | warning ([`strict`](#strict-mode) → error) | [`sqlSyntaxCheck` with a dialect](/sql-syntax-check#choosing-a-dialect) |
| [`KUERY_REDUNDANT_TRIM_INDENT`](#redundant-trimindent) | An explicit `trimIndent()` is redundant under auto-trim | warning (style — never escalated) | [`autoTrimIndent`](/basics#automatic-trimindent-opt-in) |

Every diagnostic can be [suppressed per declaration or reconfigured project-wide](#configuring-the-severity-manually)
with the standard Kotlin mechanisms (`@Suppress`, `-Xwarning-level`).

## Unsafe SQL strings

The plugin's conversion of string interpolation into named bind parameters only works when the
SQL string is written **directly as a string literal/template at the call site**. If you pass
anything else — for example a variable — the plugin cannot see the interpolation, and the
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

### Responding to the warning

In order of preference:

1. **Restructure into safe forms.** Most dynamic SQL can be expressed with Kotlin control flow
   inside the `sql { }` block — `if` / `when` / loops around `+"..."` — which the plugin handles
   safely. See [Building SQL](/basics#dynamic-sql-with-kotlin-control-flow).
2. **Suppress the warning** with `@Suppress("KUERY_UNSAFE_SQL_STRING")` on the declaration, when
   you know the string is safe but the checker cannot see it.

## `bind()` in a string template

[`bind()`](/helpers#writing-a-custom-helper) only makes sense together with
`addUnsafe()`. Calling it inside a string template passed to `add()` / `+` is reported as a
`KUERY_BIND_CALL_IN_SQL_TEMPLATE` compile **error**:

```kotlin
kueryClient.sql {
    +"SELECT * FROM users WHERE user_id = ${bind(userId)}" // KUERY_BIND_CALL_IN_SQL_TEMPLATE
}
```

Interpolated values there are bound automatically, so the placeholder name returned by `bind()`
(e.g. `:p0`) would itself be re-bound as a new parameter value — the query would compare
`user_id` against the literal string `:p0` and silently match nothing. Since there is no valid
program in which this may be left as-is, it is an **error regardless of strict mode**.
Interpolate the value directly (`$userId` instead of `${bind(userId)}`), or use `addUnsafe()`
when you need `bind()` — see [Helpers](/helpers#writing-a-custom-helper).

## SQL syntax validation (opt-in)

With the `sqlSyntaxCheck` option enabled, the plugin also validates the **SQL text itself** at
compile time: blocks whose complete statement is statically known are assembled exactly like at
runtime and run through a SQL parser. A parse failure is reported as `KUERY_SQL_SYNTAX`, and —
when a dialect is configured — a feature the dialect does not support as `KUERY_SQL_DIALECT`.
This check has its own page: see [SQL Syntax Check](/sql-syntax-check).

## Redundant `trimIndent()`

With the [`autoTrimIndent`](/basics#automatic-trimindent-opt-in) option enabled, every string
passed to `add()` / `+` is trimmed automatically, so an explicit `.trimIndent()` left behind is
redundant — it prevents the compile-time trimming and only adds an extra runtime trim. The
plugin reports a `KUERY_REDUNDANT_TRIM_INDENT` **warning** for such calls; remove them when
enabling the option. Details are in [Basics](/basics#automatic-trimindent-opt-in).

This is a style issue, not a safety issue, so [strict mode](#strict-mode) does not escalate it.

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

The checks are regular Kotlin compiler diagnostics, so the standard Kotlin mechanisms apply to
every diagnostic in the table (except `KUERY_BIND_CALL_IN_SQL_TEMPLATE`, whose error severity
cannot be lowered):

- **Suppress for specific code** with `@Suppress("<name>")` on the enclosing declaration, e.g.
  `@Suppress("KUERY_SQL_SYNTAX")`. This also works for diagnostics escalated to errors.
- **Escalate a single diagnostic** without strict mode:

  ```kotlin
  kotlin {
      compilerOptions {
          freeCompilerArgs.add("-Xwarning-level=KUERY_UNSAFE_SQL_STRING:error")
      }
  }
  ```

- **Lower one back to a warning** with strict mode enabled (see [above](#strict-mode)), or
  **disable one project-wide** with `-Xwarning-level=<name>:disabled` (not recommended).
- `allWarningsAsErrors` (`-Werror`) likewise turns all warnings into errors.
