# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Kuery Client is a Kotlin SQL client library that enables writing raw SQL using Kotlin string interpolation. It is built on top of Spring Data R2DBC and Spring Data JDBC. A Kotlin compiler plugin transparently converts string interpolation inside `SqlBuilder.add()` / `+"..."` calls into named parameter binding, preventing SQL injection.

## Commands

```bash
# Build all modules
./gradlew build

# Run all tests (requires Docker for Testcontainers)
./gradlew test

# Run tests for a single module
./gradlew :kuery-client-core:test
./gradlew :kuery-client-spring-data-r2dbc:test
./gradlew :kuery-client-spring-data-jdbc:test
./gradlew :kuery-client-compiler:test

# Run a single test class
./gradlew :kuery-client-spring-data-r2dbc:test --tests "dev.hsbrysk.kuery.spring.r2dbc.BasicUsageTest"

# Lint (ktlint + detekt)
./gradlew ktlintCheck
./gradlew detektMain detektTest

# Auto-format
./gradlew ktlintFormat
```

Integration tests (`kuery-client-spring-data-r2dbc`, `kuery-client-spring-data-jdbc`) spin up a MySQL container via Testcontainers and require Docker to be running.

## Module Structure

| Module | Description |
|---|---|
| `kuery-client-core` | Core interfaces and SQL builder. No Spring dependency. |
| `kuery-client-compiler` | Kotlin compiler plugin (IR transformation). |
| `kuery-client-gradle-plugin` | Gradle plugin that wires the compiler plugin into user projects. |
| `kuery-client-spring-data-r2dbc` | `KueryClient` implementation using Spring Data R2DBC (coroutines). |
| `kuery-client-spring-data-jdbc` | `KueryBlockingClient` implementation using Spring Data JDBC (blocking). |
| `kuery-client-detekt` | Custom detekt rules for kuery-client usage. |
| `build-logic` | Convention Gradle plugins shared across modules. |

## Architecture

### Compiler Plugin (the key mechanism)

`SqlBuilder.add(sql)` and `+"sql"` (i.e., `String.unaryPlus`) are **intentionally broken at runtime** without the compiler plugin — they throw `error("kuery-client-compiler plugin is not loaded...")`. The plugin (`StringInterpolationTransformer`) intercepts calls to these two methods at the IR level and rewrites any `IrStringConcatenation` inside them into a call to `DefaultSqlBuilder.interpolate(fragments, values)`, which performs proper named-parameter binding.

This means `+"SELECT * FROM users WHERE id = $userId"` becomes equivalent to a parameterized query with `:p0 = userId` — the user never writes placeholders manually.

### Core Abstractions

- `SqlBuilder` — DSL interface for building SQL. Users call `add()` / `+"..."` (processed by the compiler plugin) or `addUnsafe()` + `bind()` for dynamic cases that can't use the plugin.
- `Sql` — Immutable value holding the final SQL body string and `List<NamedSqlParameter>`.
- `KueryClient` (suspending) / `KueryBlockingClient` (blocking) — Execute a `SqlBuilder` block and return a `FetchSpec`.
- `FetchSpec` — Terminal operations: `single()`, `singleOrNull()`, `list()`, `flow()`, `rowsUpdated()`, `generatedValues()`.

### Spring Implementations

`DefaultSpringR2dbcKueryClient` and `DefaultSpringJdbcKueryClient` wrap Spring's `DatabaseClient` / `NamedParameterJdbcTemplate`. They handle:
- Custom type conversions via `ConversionService` and `R2dbcCustomConversions` / `JdbcCustomConversions`
- Enum → name serialization by default
- Micrometer Observation instrumentation (optional)
- Auto SQL ID generation from the call-site lambda reference (for metrics)

Row mapping uses `DataClassRowMapper` (Spring's data class mapper) for complex types and `SingleColumnRowMapper` for simple scalar types.

### Build Conventions

All modules apply `conventions.preset.base` (= `conventions.kotlin` + `conventions.ktlint` + `conventions.detekt`). The Kotlin toolchain is Java 17 (Adoptium). `allWarningsAsErrors = true` is enforced. Versions are centralized in `gradle/libs.versions.toml`.

### `@DelicateKueryClientApi`

`SqlBuilder.addUnsafe()` and `bind()` are annotated with `@DelicateKueryClientApi` and require opt-in. They are for cases where the compiler plugin cannot be used (e.g., helper extension functions that dynamically build SQL fragments).
