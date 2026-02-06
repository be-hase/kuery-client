# CLAUDE.md

## Project Overview

Kuery Client is a Kotlin SQL client library that lets you write SQL as-is using Kotlin string interpolation. A Kotlin compiler plugin transforms string interpolation into parameterized SQL with bind variables, preventing SQL injection. The library integrates with Spring Data JDBC (blocking) and Spring Data R2DBC (reactive/coroutines).

- **Author:** Ryosuke Hasebe (be-hase)
- **License:** MIT
- **Published to:** Maven Central and Gradle Plugin Portal
- **Group ID:** `dev.hsbrysk.kuery-client`

## Module Structure

```
kuery-client/
├── build-logic/                          # Gradle convention plugins
├── kuery-client-core/                    # Core interfaces (KueryClient, SqlBuilder, FetchSpec)
├── kuery-client-compiler/                # Kotlin compiler plugin (IR string interpolation transform)
│   └── functional-test/                  # Compiler plugin functional tests
├── kuery-client-gradle-plugin/           # Gradle plugin to apply the compiler plugin
├── kuery-client-spring-data-jdbc/        # Spring Data JDBC implementation (blocking)
├── kuery-client-spring-data-r2dbc/       # Spring Data R2DBC implementation (reactive/coroutines)
├── kuery-client-detekt/                  # Custom Detekt rules for kuery-client
├── examples/                             # Example Spring Boot applications
│   ├── spring-data-jdbc/
│   └── spring-data-r2dbc/
├── docs/                                 # VitePress documentation site
└── config/detekt/                        # Detekt configuration
```

## Build System

- **Gradle** with Kotlin DSL
- **Version catalog:** `gradle/libs.versions.toml`
- **Convention plugins** in `build-logic/src/main/kotlin/conventions/`:
  - `conventions.kotlin` — Kotlin JVM setup, Java 17, allWarningsAsErrors
  - `conventions.ktlint` — KtLint formatting
  - `conventions.detekt` — Detekt static analysis
  - `conventions.preset.base` — Aggregates kotlin + ktlint + detekt, sets group/version
  - `conventions.maven-publish` — Maven Central publishing with Dokka + signing
  - `conventions.jmh` — JMH benchmarking

## Essential Commands

```bash
# Run all checks (tests + detekt + ktlint)
./gradlew check detektMain detektTest

# Run checks on examples too (mirrors CI)
./gradlew check detektMain detektTest && ./gradlew -p examples check detektMain detektTest

# Run tests only
./gradlew test

# Run tests for a specific module
./gradlew :kuery-client-core:test
./gradlew :kuery-client-spring-data-jdbc:test
./gradlew :kuery-client-spring-data-r2dbc:test
./gradlew :kuery-client-compiler:test
./gradlew :kuery-client-compiler:functional-test:test

# Format code with ktlint
./gradlew ktlintFormat

# Run detekt only
./gradlew detektMain detektTest

# Build without tests
./gradlew assemble
```

## CI Pipeline

Defined in `.github/workflows/ci.yml`. Runs on PRs and pushes to `main`:
1. `check` job: JDK 17 (Temurin) + `./gradlew check detektMain detektTest` for root and examples
2. `report-gradle-dependency-diff` job: Reports dependency changes on PRs

## Key Technologies & Versions

| Technology | Version |
|---|---|
| Kotlin | 2.2.21 |
| Java target | 17 (Adoptium/Temurin) |
| Spring Boot | 3.5.10 |
| Spring Data | 3.5.8 |
| Kotlin Coroutines | 1.10.2 |
| Micrometer | 1.16.2 |
| JUnit | 6.0.2 |
| AssertK | 0.28.1 |
| MockK | 1.14.9 |
| Detekt | 1.23.8 |
| KtLint | 1.8.0 |

## Code Style & Conventions

### Formatting Rules
- **Max line length:** 120 characters
- **Indentation:** 4 spaces
- **No wildcard imports** (star import threshold set to max int)
- **Trailing commas** allowed on declarations and call sites
- **Line endings:** LF
- **ktlint style:** `intellij_idea`
- Force multiline when 2+ parameters (class signatures and function signatures)
- All Kotlin warnings treated as errors (`allWarningsAsErrors = true`)
- JSR305 strict mode: `-Xjsr305=strict`

### Naming Conventions
- **Packages:** `dev.hsbrysk.kuery.{module}` (e.g., `core`, `spring.jdbc`, `spring.r2dbc`, `compiler`)
- **Interfaces:** PascalCase, no prefix (e.g., `KueryClient`, `SqlBuilder`, `FetchSpec`)
- **Internal implementations:** `Default{InterfaceName}` in `.internal` subpackages
- **Test classes:** `{Feature}Test` (e.g., `BasicUsageTest`, `SingleBasicTypeTest`)
- **Test methods:** camelCase or backtick-quoted for readability (e.g., `` `singleMap no record`() ``)

### Design Patterns
- **Interface-first:** Public APIs are interfaces; implementations are `internal`
- **Builder pattern:** For client construction (`SpringJdbcKueryClient.builder()...build()`)
- **Kotlin DSL:** `@SqlBuilderMarker` annotation, `SqlBuilder.() -> Unit` lambdas
- **Extension functions:** Reified inline functions for type-safe generics (e.g., `FetchSpec.single<T>()`)
- **Operator overloading:** `String.unaryPlus()` for SQL building (`+"SELECT * FROM ..."`)
- **OptIn annotations:** `@DelicateKueryClientApi` for advanced/unsafe APIs
- **Suspend functions:** For async R2DBC operations; separate `KueryBlockingClient` for JDBC

### Error Handling
- `requireNotNull()` and `require()` for precondition validation
- Spring Data exceptions propagate naturally (no custom wrapping)
- Compiler plugin throws `error()` when not loaded

## Testing

### Frameworks
- **JUnit 5** (Jupiter) via JUnit BOM
- **AssertK** for fluent assertions
- **MockK** for mocking
- **TestContainers** (MySQL) for integration tests in JDBC and R2DBC modules
- **kotlin-compile-testing** for compiler plugin unit tests
- **Micrometer Observation Test** for observability testing

### Test Structure
- Unit tests in `src/test/` of each module
- Compiler plugin functional tests in `kuery-client-compiler/functional-test/`
- JMH benchmarks in `src/jmh/` of `kuery-client-spring-data-r2dbc`
- Integration tests require Docker (TestContainers launches MySQL)

### Running Tests
Integration tests in `kuery-client-spring-data-jdbc` and `kuery-client-spring-data-r2dbc` require Docker to be available for TestContainers. The compiler `functional-test` module tests the actual compiler plugin transformations end-to-end.

## How the Compiler Plugin Works

The core feature of kuery-client: Kotlin string interpolation in `SqlBuilder` blocks is transformed at compile time into parameterized SQL.

```kotlin
// What you write:
kueryClient.sql {
    +"SELECT * FROM users WHERE user_id = $userId"
}

// What the compiler plugin transforms it to (conceptually):
// SQL: "SELECT * FROM users WHERE user_id = ?"
// Parameters: [userId]
```

Key compiler plugin files:
- `KueryClientCompilerPluginRegistrar.kt` — Entry point, registers IR extension
- `ir/StringInterpolationTransformer.kt` — Main IR transformation logic
- `ir/KueryClientiIrGenerationExtension.kt` — IR generation extension
- Supports K2 compiler

## Documentation

- VitePress documentation in `docs/` directory
- KDoc on public API interfaces and methods
- Examples in `examples/` directory (Spring Boot applications for JDBC and R2DBC)
