---
description: The opt-in KUERY_SQL_SYNTAX compiler warning that validates statically-known SQL with a SQL parser at compile time.
---

# SQL Syntax Check (opt-in)

Kuery Client lets you write raw SQL, so a typo like `SELCT` or a missing parenthesis normally
surfaces only at runtime, as a database error. The compiler plugin can optionally validate the
SQL syntax of your `sql { }` blocks **at compile time**. Enable it in the Gradle plugin:

```kotlin
kueryClient {
    sqlSyntaxCheck = true
}
```

(On Gradle versions without Kotlin DSL property assignment — before 8.2 — write
`sqlSyntaxCheck.set(true)` instead.)

A block whose SQL fails to parse is then reported as a `KUERY_SQL_SYNTAX` **warning**, anchored
to the offending line:

```kotlin
kueryClient.sql {
    +"SELECT *"
    +"FORM users"          // KUERY_SQL_SYNTAX: Encountered unexpected token: "users" ...
    +"WHERE user_id = $userId"
}
```

## What is checked

A block is validated only when the **complete statement is statically known**: every statement
in the block is an `add()` / `+"..."` whose argument is a string literal/template, a `const val`
reference, or `trimIndent()` / `trimMargin()` on one. The statement texts are assembled exactly
like at runtime — joined with newlines, every interpolated `$value` replaced by its `:pN` bind
placeholder — and parsed.

Anything else makes the whole block **silently skipped**, because the final SQL cannot be known
at compile time:

- Conditionals or loops inside the block (`if`, `when`, `for`, `?.let { ... }`)
- Calls to helper functions that append fragments
- `addUnsafe()` or non-literal arguments (those already draw
  [`KUERY_UNSAFE_SQL_STRING`](/compiler-safety-check))

This mirrors how compile-time-checked SQL works elsewhere (e.g. Rust's sqlx): statements that
are fully known are verified, dynamically assembled ones are not — no false alarms on dynamic
SQL.

## False positives and limitations

The check uses [JSqlParser](https://github.com/JSQLParser/JSqlParser), a generic multi-dialect
SQL parser, and runs without connecting to a database. Consequences:

- **It is a syntax check only.** Table/column names, types, and schema are not verified.
- **Some invalid SQL passes.** JSqlParser is deliberately lenient; the check catches broken
  statements, not every mistake.
- **Rare vendor-specific syntax may be flagged although your database accepts it.** In that
  case suppress the warning for the declaration:

```kotlin
@Suppress("KUERY_SQL_SYNTAX")
fun listUsersWithVendorSyntax(): List<User> = ...
```

## Configuring the severity

Like every diagnostic of the plugin, the standard Kotlin mechanisms apply:

- Treat it as an error: `-Xwarning-level=KUERY_SQL_SYNTAX:error`, or enable
  `allWarningsAsErrors` (`-Werror`)
- Disable it for specific code with `@Suppress("KUERY_SQL_SYNTAX")`, or project-wide with
  `-Xwarning-level=KUERY_SQL_SYNTAX:disabled` (or simply leave `sqlSyntaxCheck` off)

The option defaults to `false`, so existing builds are unaffected.
