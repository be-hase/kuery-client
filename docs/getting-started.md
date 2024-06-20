# Getting Started

## Install

### Gradle

::: code-group

```kotlin [kuery-client-spring-data-r2dbc]
implementation("dev.hsbrysk.kuery-client:kuery-client-spring-data-r2dbc:{{version}}")
```

```kotlin [kuery-client-spring-data-jdbc]
implementation("dev.hsbrysk.kuery-client:kuery-client-spring-data-jdbc:{{version}}")
```

:::

### Maven

::: code-group

```xml [kuery-client-spring-data-r2dbc]
<dependency>
    <groupId>dev.hsbrysk.kuery-client</groupId>
    <artifactId>kuery-client-spring-data-r2dbc</artifactId>
    <version>{{version}}</version>
</dependency>
```

```xml [kuery-client-spring-data-jdbc]
<dependency>
    <groupId>dev.hsbrysk.kuery-client</groupId>
    <artifactId>kuery-client-spring-data-jdbc</artifactId>
    <version>{{version}}</version>
</dependency>
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
val user: User = kueryClient
    .sql { +"SELECT * FROM users WHERE user_id = 1" }
    .singleOrNull()
```
