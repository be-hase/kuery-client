---
description: All compile-time diagnostics reported by the kuery-client compiler plugin — unsafe SQL strings, bind() misuse, SQL syntax validation, redundant trimIndent — and the strict option that escalates the safety warnings into compile errors.
---

# Compile-Time Checks

The kuery-client compiler plugin does more than rewrite interpolated runtime values into bind
parameters — it also **checks your SQL code at compile time**. This page is the overview of
every diagnostic the plugin can report:

| Diagnostic | Fires when | Severity | Enabled |
|---|---|---|---|
| [`KUERY_UNSAFE_SQL_STRING`](#unsafe-sql-strings) | Automatic parameter binding cannot be guaranteed for a SQL expression | warning ([`strict`](#strict-mode) → error) | always |
| [`KUERY_BIND_CALL_IN_SQL_TEMPLATE`](#bind-in-a-string-template) | `bind()` is called inside a string template | always error | always |
| [`KUERY_SQL_SYNTAX`](#sql-syntax-errors) | Statically-known SQL fails to parse | warning ([`strict`](#strict-mode) → error) | [`sqlSyntaxCheck`](/sql-syntax-check) |
| [`KUERY_SQL_DIALECT`](#unsupported-sql-dialect-features) | SQL uses a feature the dialect lacks | warning ([`strict`](#strict-mode) → error) | [`sqlSyntaxCheck` with a dialect](/sql-syntax-check#choosing-a-dialect) |
| [`KUERY_REDUNDANT_TRIM_INDENT`](#redundant-trimindent) | An explicit `trimIndent()` is redundant under auto-trim | warning (style — never escalated) | [`autoTrimIndent`](/basics#automatic-trimindent-opt-in) |

Every diagnostic can be [suppressed per declaration or reconfigured project-wide](#configuring-the-severity-manually)
with the standard Kotlin mechanisms (`@Suppress`, `-Xwarning-level`).

## Unsafe SQL strings (`KUERY_UNSAFE_SQL_STRING`) {#unsafe-sql-strings}

The plugin's conversion of string interpolation into named bind parameters only works when the
SQL string is written **directly as a string literal/template at the call site**. If you pass
anything else — for example a variable — the plugin cannot see the interpolation, and the
string would be executed as raw SQL.

To prevent this from happening silently, the compiler plugin reports a
`KUERY_UNSAFE_SQL_STRING` **warning** when it cannot guarantee that interpolation in an `add()` /
`+` expression will be converted into bind parameters.

### Noncompliant code

```kotlin
// String variable: interpolation already happened before add()
val sql = "SELECT * FROM users WHERE user_id = $userId"
kueryClient.sql {
    add(sql) // KUERY_UNSAFE_SQL_STRING
}

// String concatenation
kueryClient.sql {
    add("SELECT * FROM users WHERE " + condition) // KUERY_UNSAFE_SQL_STRING
}

// Function call: the plugin cannot see what the function returns
kueryClient.sql {
    add(buildWhere()) // KUERY_UNSAFE_SQL_STRING
}

// Runtime transformation of a literal
kueryClient.sql {
    add("WHERE aaa".replace("aaa", input)) // KUERY_UNSAFE_SQL_STRING
}

// Scope function: the plugin does not inspect the lambda body
kueryClient.sql {
    add(run { "SELECT 1" }) // KUERY_UNSAFE_SQL_STRING
}

// Every branch must be safe; fragment is a String variable
kueryClient.sql {
    add(if (id > 0) "SELECT 1" else fragment) // KUERY_UNSAFE_SQL_STRING
}
```

### Compliant code

```kotlin
kueryClient.sql {
    +"SELECT * FROM users WHERE user_id = $userId" // userId is bound as :p0
}
```

Here the SQL template is passed directly, so the plugin can rewrite `userId` into a bind
parameter. Other accepted forms include:

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

## `bind()` in a string template (`KUERY_BIND_CALL_IN_SQL_TEMPLATE`) {#bind-in-a-string-template}

[`bind()`](/helpers#writing-a-custom-helper) only makes sense together with
`addUnsafe()`. Calling it inside a string template passed to `add()` / `+` is reported as a
`KUERY_BIND_CALL_IN_SQL_TEMPLATE` compile **error**. Interpolated values there are already bound
automatically, so the placeholder returned by `bind()` would itself be bound as a value. The SQL
would compare `user_id` against the literal string `:p0` and silently match nothing.

### Noncompliant code

```kotlin
kueryClient.sql {
    +"SELECT * FROM users WHERE user_id = ${bind(userId)}" // KUERY_BIND_CALL_IN_SQL_TEMPLATE
}
```

### Compliant code

```kotlin
kueryClient.sql {
    +"SELECT * FROM users WHERE user_id = $userId"
}
```

This diagnostic is an **error regardless of strict mode**. Interpolate the value directly, or use
`addUnsafe()` when you need `bind()` for dynamically assembled SQL — see
[Helpers](/helpers#writing-a-custom-helper).

## SQL syntax errors (`KUERY_SQL_SYNTAX`) {#sql-syntax-errors}

With the `sqlSyntaxCheck` option enabled, the plugin also validates the **SQL text itself** at
compile time: blocks whose complete statement is statically known are assembled exactly like at
runtime and run through a SQL parser. A parse failure is reported as `KUERY_SQL_SYNTAX`. See
[SQL Syntax Check](/sql-syntax-check) for configuration and examples.

### Noncompliant code

```kotlin
kueryClient.sql {
    +"SELECT *"
    +"FORM users" // KUERY_SQL_SYNTAX: FORM should be FROM
}
```

### Compliant code

```kotlin
kueryClient.sql {
    +"SELECT *"
    +"FROM users"
}
```

## Unsupported SQL dialect features (`KUERY_SQL_DIALECT`) {#unsupported-sql-dialect-features}

When `sqlSyntaxCheck` is configured with a dialect, the plugin also reports SQL features that the
selected dialect does not support as `KUERY_SQL_DIALECT`. See
[Choosing a dialect](/sql-syntax-check#choosing-a-dialect) for the available dialects and examples.

The following examples assume `sqlSyntaxCheck = "postgresql"`.

### Noncompliant code

```kotlin
// KUERY_SQL_DIALECT: ON DUPLICATE KEY UPDATE is a MySQL feature
kueryClient.sql {
    +"INSERT INTO users (id, name) VALUES ($id, $name) ON DUPLICATE KEY UPDATE name = $name"
}
```

### Compliant code

```kotlin
kueryClient.sql {
    +"INSERT INTO users (id, name) VALUES ($id, $name) ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name"
}
```

## Redundant `trimIndent()` (`KUERY_REDUNDANT_TRIM_INDENT`) {#redundant-trimindent}

With the [`autoTrimIndent`](/basics#automatic-trimindent-opt-in) option enabled, every string
passed to `add()` / `+` is trimmed automatically, so an explicit `.trimIndent()` left behind is
redundant — it prevents the compile-time trimming and only adds an extra runtime trim. The
plugin reports a `KUERY_REDUNDANT_TRIM_INDENT` **warning** for such calls; remove them when
enabling the option. Details are in [Basics](/basics#automatic-trimindent-opt-in).

This is a style issue, not a safety issue, so [strict mode](#strict-mode) does not escalate it.

### Noncompliant code

```kotlin
kueryClient.sql {
    +"""
    SELECT *
    FROM users
    """.trimIndent() // KUERY_REDUNDANT_TRIM_INDENT
}
```

### Compliant code

```kotlin
kueryClient.sql {
    +"""
    SELECT *
    FROM users
    """
}
```

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
