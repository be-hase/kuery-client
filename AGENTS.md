# AGENTS.md

This file provides guidance to coding agents (Claude Code, Codex, etc.) when working with code in this repository. It is the canonical source; tool-specific entry points such as `CLAUDE.md` point here.

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
./gradlew :kuery-client-spring-data-r2dbc:test --tests "dev.hsbrysk.kuery.spring.r2dbc.usage.BasicUsageTest"

# Lint (ktlint + detekt)
./gradlew ktlintCheck
./gradlew detektMain detektTest

# Auto-format
./gradlew ktlintFormat

# Update ABI reference dumps (run after changing public API; commit the api/*.api files)
./gradlew updateKotlinAbi
```

Integration tests (`kuery-client-spring-data-r2dbc`, `kuery-client-spring-data-jdbc`) spin up a MySQL container via Testcontainers and require Docker to be running.

## Module Structure

| Module | Description |
|---|---|
| `kuery-client-core` | Core interfaces and SQL builder. No Spring dependency. |
| `kuery-client-compiler` | Kotlin compiler plugin (IR transformation). |
| `kuery-client-gradle-plugin` | Gradle plugin that wires the compiler plugin into user projects. |
| `kuery-client-spring-data-common` | Internal support shared by the two Spring Data implementations (value class reflection, read/write conversion). Published as their runtime dependency but exposes no public API. |
| `kuery-client-spring-data-r2dbc` | `KueryClient` implementation using Spring Data R2DBC (coroutines). |
| `kuery-client-spring-data-jdbc` | `KueryBlockingClient` implementation using Spring Data JDBC (blocking). |
| `build-logic` | Convention Gradle plugins shared across modules. |

## Architecture

### Compiler Plugin (the key mechanism)

`SqlBuilder.add(sql)` and `+"sql"` (i.e., `String.unaryPlus`) are **intentionally broken at runtime** without the compiler plugin — they throw an `IllegalStateException` explaining that the compiler plugin must be applied. The plugin (`StringInterpolationTransformer`) intercepts calls to these two methods at the IR level and rewrites any `IrStringConcatenation` inside them into a call to `DefaultSqlBuilder.interpolate(fragments, values)`, which performs proper named-parameter binding.

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

`kuery-client-core`, `kuery-client-spring-data-common`, `kuery-client-spring-data-jdbc`, and `kuery-client-spring-data-r2dbc` additionally apply `conventions.public-api`, which enables Kotlin explicit API mode and KGP built-in ABI validation. `checkKotlinAbi` runs as part of `check`; when the public API changes intentionally, run `./gradlew updateKotlinAbi` and commit the updated `api/*.api` files. Declarations annotated with `@KueryClientInternalApi` are excluded from the ABI dumps and carry no compatibility guarantee. The `kuery-client-spring-data-common` dump is intentionally empty — every declaration there is `@KueryClientInternalApi`, public only so the jdbc/r2dbc modules can reach it.

### `@DelicateKueryClientApi`

`SqlBuilder.addUnsafe()` and `bind()` are annotated with `@DelicateKueryClientApi` and require opt-in. They are for cases where the compiler plugin cannot be used (e.g., helper extension functions that dynamically build SQL fragments).

## Testing Conventions

### Naming & Structure

- Test method names are backtick-quoted English specification sentences that include both the condition and the expected outcome, so the name alone reads as a spec (e.g., `` `singleMap throws when no rows match` ``). No `should`/`test` prefixes; use third-person singular present. An operation name alone (e.g., `singleMap`) is not enough.
- Structure test bodies with lowercase `// given` / `// when` / `// then` comments (`// when & then` when combined). Omit them for trivial one/two-line tests or repetitive structures — don't paste them mechanically.
- **One test function = one spec.** If multiple independently specifiable behaviors live in one function (happy path + error path, different behavior per type, etc.), split them. Do NOT split: (a) side-by-side tests where the contrast itself is the point, (b) incremental examples of the same rule (n=1/n=2), (c) tests exercising a single conditional expression with different inputs.

### jdbc / r2dbc Mirror Convention

The `kuery-client-spring-data-jdbc` and `kuery-client-spring-data-r2dbc` test suites mirror each other by feature subpackage (`conversion` / `mapping` / `usage` / `observation` / `sqlid` / `mysql` / `postgres`). Corresponding tests must use **identical class and method names** across both modules. One-sided classes are allowed only for genuinely one-sided features (e.g., Reactor context on r2dbc, `Sequence` on jdbc).

### Database Test Matrix

Pick the database by asking: "can this behavior differ per driver/DB code path?"

- **Our own layer** (builder, mapper wrapping, terminal operations): use the in-memory H2 setup (`H2TestDatabase`) in the module's root test package — no container needed. H2 is configured with MySQL-like identifier behavior.
- **Driver-dependent behavior** (binding, codecs, type mapping, generated keys, etc.): use the `mysql` / `postgres` subpackages, which run real databases via Testcontainers (only the dialects where the behavior matters — both are not required). Keep the `MySql` / `Postgres` class-name prefix even inside the dialect package (e.g., `mysql.MySqlNullBindingTest`).
- Caveat: r2dbc-h2 executes queries synchronously, so tests that depend on actual suspension (e.g., Observation propagation) must go in the `mysql` package instead.

### Compiler Plugin Test Placement

- Tests that verify the **behavior** of transformed code go in `kuery-client-compiler/functional-test` (real plugin applied); behavior with auto-trim enabled goes in `functional-test-auto-trim`.
- `kuery-client-compiler/src/test` (kotlin-compile-testing) is **only** for things observable at compile level: FIR diagnostics, compilation failures, bytecode inspection.
- The `build.gradle.kts` files of `functional-test` and `functional-test-auto-trim` mirror each other — add dependencies to both.

### Gradle Plugin Test Placement

- `kuery-client-gradle-plugin/src/test` (ProjectBuilder + mocks) covers the plugin's own logic in isolation.
- `kuery-client-gradle-plugin/functional-test` (Gradle TestKit) black-box tests the **published** plugin path: the three artifacts are published to a build-local Maven repository (`build/functional-test-repo`) and a generated consumer project applies the plugin by id. This module must NOT declare project dependencies on the plugin or the compiler — resolving everything through the repository is the point.

### Bug Fixes Are Test-First

Write a reproducing test first and confirm it fails against the current implementation, then fix, then confirm green. This proves the fix actually addresses the bug.
