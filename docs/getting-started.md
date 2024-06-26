# Getting Started

## Install

### Gradle

::: code-group

```kotlin [kuery-client-spring-data-r2dbc]
plugins {
    // It has not been approved on the Gradle Plugin Portal yet... Please wait a moment.
    id("dev.hsbrysk.kuery-client") version "{{version}}"
}

implementation("dev.hsbrysk.kuery-client:kuery-client-spring-data-r2dbc:{{version}}")
```

```kotlin [kuery-client-spring-data-jdbc]
plugins {
    // It has not been approved on the Gradle Plugin Portal yet... Please wait a moment.
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

## Let's Use It

```kotlin
val userId = "..."
val user: User = kueryClient
    .sql { +"SELECT * FROM users WHERE user_id = $userId" }
    .singleOrNull()
```
