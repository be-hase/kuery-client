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

A block whose SQL fails to parse is then reported as a `KUERY_SQL_SYNTAX` **warning** (or a
compile **error** when `strict` is enabled — see
[Compile-Time Checks](/compiler-safety-check#strict-mode)), anchored to the offending line:

```kotlin
kueryClient.sql {
    +"SELECT *"
    +"FORM users"          // KUERY_SQL_SYNTAX: Encountered unexpected token: "users" ...
    +"WHERE user_id = $userId"
}
```

## What is checked

A block is validated only when the **complete statement is statically known**. The checker joins
its fragments with newlines, replaces every interpolated value with its `:pN` bind placeholder,
and parses the resulting SQL.

### Checked code

The simplest case is a multiline SQL template written directly in the block:

```kotlin
kueryClient.sql {
    +"""
    SELECT user_id, username
    FROM users
    WHERE tenant_id = $tenantId
    """
}

// Parsed as:
// SELECT user_id, username
// FROM users
// WHERE tenant_id = :p0
```

String literals/templates, `const val` references, and `trimIndent()` / `trimMargin()` on those
forms can all be reconstructed:

```kotlin
const val SELECT_USERS = "SELECT * FROM users"

kueryClient.sql {
    +SELECT_USERS
    +"""
    WHERE tenant_id = $tenantId
    """.trimIndent()
    +"""
    |ORDER BY user_id
    """.trimMargin()
}

// Parsed as:
// SELECT * FROM users
// WHERE tenant_id = :p0
// ORDER BY user_id
```

Calls to your own **static helper functions** are inlined into the reconstruction: a final
`SqlBuilder` extension in the same module — top-level or a member of the enclosing class (the
common repository pattern) — whose body is itself only static `add()` / `+"..."` statements (or
further such helpers). Its interpolated parameters become the same `:pN` binds they are at
runtime, so this works regardless of the arguments:

```kotlin
fun SqlBuilder.paging(limit: Int, offset: Int) {
    add("LIMIT $limit OFFSET $offset")
}

kueryClient.sql {
    +"SELECT * FROM users WHERE id = $id"
    paging(10, 0)                 // checked as "... LIMIT :p1 OFFSET :p2"
}
```

### Skipped code

If control flow determines which fragments are added, the final SQL depends on runtime state, so
the whole block is skipped:

```kotlin
kueryClient.sql {
    +"SELECT * FROM users"
    +"WHERE tenant_id = $tenantId"

    if (activeOnly) {
        +"AND active = TRUE"
    }
    for (role in requiredRoles) {
        +"AND role_name = $role"
    }
}
// No KUERY_SQL_SYNTAX check: the final statement varies at runtime.
```

A helper with dynamic control flow also makes its caller impossible to reconstruct:

```kotlin
fun SqlBuilder.activeUsersOnly(enabled: Boolean) {
    if (enabled) {
        +"WHERE active = TRUE"
    }
}

kueryClient.sql {
    +"SELECT * FROM users"
    activeUsersOnly(activeOnly)
}
// No KUERY_SQL_SYNTAX check.
```

`addUnsafe()` and non-literal arguments likewise opt the block out of syntax validation:

```kotlin
val orderBy = "ORDER BY user_id"

kueryClient.sql {
    +"SELECT * FROM users"
    add(orderBy) // Also reports KUERY_UNSAFE_SQL_STRING.
}

@OptIn(DelicateKueryClientApi::class)
fun customQuery(customSql: String) = kueryClient.sql {
    +"SELECT * FROM users"
    addUnsafe(customSql)
}
```

The same skip applies to `when`, `?.let { ... }`, `return@sql`, and helpers that are overridable
or compiled in another module. Skipping is silent: it suppresses only `KUERY_SQL_SYNTAX` /
`KUERY_SQL_DIALECT`, not other diagnostics such as
[`KUERY_UNSAFE_SQL_STRING`](/compiler-safety-check#unsafe-sql-strings).

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

A dialect feature violation is reported as a separate `KUERY_SQL_DIALECT` warning. Unlike the
syntax warning, it is anchored to the whole `sql { }` call (the parser's feature validation
carries no line positions):

```kotlin
// KUERY_SQL_DIALECT: insertUseDuplicateKeyUpdate not supported. (dialect: postgresql)
kueryClient.sql {
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
- **Rare vendor-specific syntax may be flagged although your database accepts it**, when
  JSqlParser has no grammar for it (a known example: H2's `MERGE INTO ... KEY(...)` upsert).
  In that case suppress the warning for the declaration:

```kotlin
@Suppress("KUERY_SQL_SYNTAX")
fun listUsersWithVendorSyntax(): List<User> = ...
```

## Configuring the severity

Like every diagnostic of the plugin, the standard Kotlin mechanisms (`strict` mode, `@Suppress`,
`-Xwarning-level`) apply — note there are two diagnostic names, `KUERY_SQL_SYNTAX` and
`KUERY_SQL_DIALECT`, configured independently. See
[Compile-Time Checks](/compiler-safety-check#configuring-the-severity-manually) for the details.

The option is unset by default, so existing builds are unaffected.
