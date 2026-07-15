---
description: Install via Gradle plugin and dependency, build a KueryClient from a ConnectionFactory or DataSource (or as a Spring Boot bean), and troubleshoot common setup issues.
---

# Getting Started

## Requirements

- Java 17 or later
- Gradle — parameter binding relies on a Kotlin compiler plugin that is applied via the Gradle plugin, so Maven
  is currently not supported
- See [Compatibility](/compatibility) for the recommended Kotlin / Spring versions for each release

## Install

### Gradle

::: code-group

```kotlin [kuery-client-spring-data-r2dbc]
plugins {
    id("dev.hsbrysk.kuery-client") version "{{version}}"
}

implementation("dev.hsbrysk.kuery-client:kuery-client-spring-data-r2dbc:{{version}}")
```

```kotlin [kuery-client-spring-data-jdbc]
plugins {
    id("dev.hsbrysk.kuery-client") version "{{version}}"
}

implementation("dev.hsbrysk.kuery-client:kuery-client-spring-data-jdbc:{{version}}")
```

:::

## Build KueryClient

::: code-group

```kotlin [kuery-client-spring-data-r2dbc]
val connectionFactory: ConnectionFactory = ...

val kueryClient = SpringR2dbcKueryClient.builder()
    .connectionFactory(connectionFactory)
    .build()
```

```kotlin [kuery-client-spring-data-jdbc]
val dataSource: DataSource = ...

val kueryClient = SpringJdbcKueryClient.builder()
    .dataSource(dataSource)
    .build()
```

:::

### With Spring Boot

When using Spring Boot, register the client as a bean using the auto-configured `ConnectionFactory` /
`DataSource`:

::: code-group

```kotlin [kuery-client-spring-data-r2dbc]
@Configuration(proxyBeanMethods = false)
class KueryClientConfiguration {
    @Bean
    fun kueryClient(connectionFactory: ConnectionFactory): KueryClient {
        return SpringR2dbcKueryClient.builder()
            .connectionFactory(connectionFactory)
            .build()
    }
}
```

```kotlin [kuery-client-spring-data-jdbc]
@Configuration(proxyBeanMethods = false)
class KueryClientConfiguration {
    @Bean
    fun kueryClient(dataSource: DataSource): KueryBlockingClient {
        return SpringJdbcKueryClient.builder()
            .dataSource(dataSource)
            .build()
    }
}
```

:::

## Let's Use It

```kotlin
data class User(
    val userId: Int,
    val username: String,
)

val userId = 1
val user: User? = kueryClient
    .sql { +"SELECT * FROM users WHERE user_id = $userId" }
    .singleOrNull()
```

For runnable Spring Boot applications, see [Examples](/examples).

## Troubleshooting

### `IllegalStateException: ... must be rewritten by the kuery-client compiler plugin`

Calling `sql { ... }` throws an exception like this at runtime:

```
`SqlBuilder.add`/`String.unaryPlus` must be rewritten by the kuery-client compiler plugin, but this call was not.
```

This means the Gradle plugin `dev.hsbrysk.kuery-client` is not applied to the module containing the call.
`+"..."` / `add(...)` are intentionally broken without the compiler plugin — otherwise string interpolation
would silently be executed as raw SQL. Apply the plugin as shown in [Install](#install) (note: in a multi-module
project, it must be applied to every module that calls `sql { ... }`).
