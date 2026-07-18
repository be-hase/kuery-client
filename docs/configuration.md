---
description: Reference for the Kuery Client Gradle plugin and runtime client-builder options, including defaults and recommended settings.
---

# Configuration

Kuery Client has two separate configuration layers:

1. The Gradle plugin configures compile-time SQL transformation and diagnostics.
2. The R2DBC/JDBC client builder configures runtime database access, conversion, and observation.

## Gradle plugin

Apply the plugin to every JVM or Android/JVM module that contains a `sql { ... }` call:

```kotlin
plugins {
    id("dev.hsbrysk.kuery-client") version "{{version}}"
}
```

The compiler plugin is applied only to JVM and Android/JVM compilations. Maven is not currently supported.
In a multi-module build, applying the plugin only to the application module is not enough when repository code
lives in another module.

### Options

| Option | Type | Default | Purpose | Accepted values |
|---|---|---|---|---|
| `autoTrimIndent` | `Boolean` | `false` | Applies `trimIndent()` automatically to strings passed to `+` / `add()` | — |
| `sqlSyntaxCheck` | `String` | unset | Enables compile-time SQL parsing and optionally selects a dialect | <ul><li><code>generic</code></li><li><code>ansi</code></li><li><code>oracle</code></li><li><code>mysql</code></li><li><code>sqlserver</code></li><li><code>mariadb</code></li><li><code>postgresql</code></li><li><code>h2</code></li></ul> |
| `strict` | `Boolean` | `false` | Escalates SQL-safety diagnostics from warnings to errors | — |

For example:

```kotlin
kueryClient {
    autoTrimIndent = true
    sqlSyntaxCheck = "postgresql"
    strict = true
}
```

`sqlSyntaxCheck` values are case-insensitive and surrounding whitespace is ignored. Any other value fails the
Gradle build.

::: tip Recommended settings and planned defaults
These options were added after Kuery Client's initial releases and remain opt-in today to avoid
changing the behavior of existing projects. They are intended to become the defaults in a future
release, equivalent to:

```kotlin
kueryClient {
    autoTrimIndent = true
    sqlSyntaxCheck = "mysql"
    strict = true
}
```

New projects can enable them now. Replace `mysql` with the project's dialect when needed;
dialect validation is stricter but can report false positives for uncommon vendor-specific SQL.
:::

See [Building SQL](/basics#automatic-trimindent-opt-in),
[Compile-Time Checks](/compiler-safety-check), and [SQL Syntax Check](/sql-syntax-check) for the exact behavior.

## Runtime client builder

The R2DBC and JDBC builders expose the same configuration concepts, with a different required connection object.

| Builder method | R2DBC | JDBC | Default |
|---|---|---|---|
| `connectionFactory(...)` | required | — | none |
| `dataSource(...)` | — | required | none |
| `converters(...)` | available | available | empty list |
| `observationRegistry(...)` | available | available | observation disabled |
| `observationConvention(...)` | available | available | built-in convention |
| `enableAutoSqlIdGeneration(...)` | available | available | `true` when an observation registry is set; otherwise `false` |

Calling `build()` without the required `ConnectionFactory` or `DataSource` throws an `IllegalArgumentException`.
Both built clients are designed to be shared across the application.

::: code-group

```kotlin [R2DBC]
val kueryClient: KueryClient = SpringR2dbcKueryClient.builder()
    .connectionFactory(connectionFactory)
    .converters(listOf(MyWritingConverter(), MyReadingConverter()))
    .observationRegistry(observationRegistry)
    .build()
```

```kotlin [JDBC]
val kueryClient: KueryBlockingClient = SpringJdbcKueryClient.builder()
    .dataSource(dataSource)
    .converters(listOf(MyWritingConverter(), MyReadingConverter()))
    .observationRegistry(observationRegistry)
    .build()
```

:::

See [Type Conversion](/type-conversion) and [Observation](/observation) for the optional runtime settings.
