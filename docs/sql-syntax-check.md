---
description: The opt-in KUERY_SQL_SYNTAX compiler warning that validates statically-known SQL with a SQL parser at compile time.
---

# SQL Syntax Check (opt-in)

Kuery Client lets you write raw SQL, so a typo like `SELCT` or a missing parenthesis normally
surfaces only at runtime, as a database error. The compiler plugin can optionally validate the
SQL syntax of your `sql { }` blocks **at compile time**. Enable it in the Gradle plugin with a
single option — `"generic"` for a dialect-agnostic syntax check, or a dialect name to also check
that dialect's feature set (see [below](#choosing-a-dialect)):

```kotlin
kueryClient {
    sqlSyntaxCheck = "generic" // or "mysql", "postgresql", ...
}
```

(On Gradle versions without Kotlin DSL property assignment — before 8.2 — write
`sqlSyntaxCheck.set("generic")` instead.)

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
- `addUnsafe()` (dynamic SQL is out of the check's scope by design)
- Non-literal `add()` arguments such as variables (those already draw
  [`KUERY_UNSAFE_SQL_STRING`](/compiler-safety-check))

This mirrors how compile-time-checked SQL works elsewhere (e.g. Rust's sqlx): statements that
are fully known are verified, dynamically assembled ones are not — no false alarms on dynamic
SQL.

## Choosing a dialect

The parser (JSqlParser) parses a superset of all dialects, so under `"generic"` a MySQL-only
construct passes even in a PostgreSQL project. If you set `sqlSyntaxCheck` to your dialect
instead, statements that pass the syntax check are additionally validated against that dialect's
feature set:

```kotlin
kueryClient {
    sqlSyntaxCheck = "postgresql"
}
```

Values: `generic`, `ansi`, `oracle`, `mysql`, `sqlserver`, `mariadb`, `postgresql`, `h2`.
`generic` runs the syntax check with no feature validation (the fewest false positives); `ansi`
is a real, strict ANSI SQL feature set, distinct from `generic`.

A dialect feature violation is reported as a separate `KUERY_SQL_DIALECT` warning:

```kotlin
kueryClient.sql {
    // KUERY_SQL_DIALECT: insertUseDuplicateKeyUpdate not supported. (dialect: postgresql)
    +"INSERT INTO users (id, name) VALUES ($id, $name) ON DUPLICATE KEY UPDATE name = $name"
}
```

This uses JSqlParser's validation framework, which is a **feature-level allow-list, not a full
dialect grammar**: it catches whole features the database does not have (upserts, `RETURNING`,
...), but some cross-dialect syntax still passes, and an incomplete allow-list can produce a
false positive — suppress those with `@Suppress("KUERY_SQL_DIALECT")`.

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
  `-Xwarning-level=KUERY_SQL_SYNTAX:disabled` (or simply leave `sqlSyntaxCheck` unset)

The option is unset by default, so existing builds are unaffected.
