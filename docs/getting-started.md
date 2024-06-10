# Getting Started

## Install

### Gradle

```kotlin
implementation("dev.hsbrysk.kuery-client:kuery-client-spring-data-r2dbc:{{version}}")
// or, implementation("dev.hsbrysk.kuery-client:kuery-client-spring-data-jdbc:{{version}}")
```

### Maven

```xml

<dependency>
    <groupId>dev.hsbrysk.kuery-client</groupId>
    <artifactId>kuery-client-spring-data-r2dbc</artifactId>
    <!-- or, <artifactId>kuery-client-spring-data-jdbc</artifactId> -->
    <version>{{version}}</version>
</dependency>
```

## Build KueryClient

### for `kuery-client-spring-data-r2dbc`

```kotlin
val connectionFactory: ConnectionFactory = ...

val kueryClient = SpringR2dbcKueryClient.builder()
    .connectionFactory(connectionFactory)
    .build()
```

### for `kuery-client-spring-data-jdbc`

```kotlin
val dataSource: DataSource = ...

val kueryClient = SpringJdbcKueryClient.builder()
    .dataSource(dataSource)
    .build()
```

## Let's Use It

```kotlin
val user: User = kueryClient
    .sql { +"SELECT * FROM users WHERE user_id = 1" }
    .singleOrNull()
```
