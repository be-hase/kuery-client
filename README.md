<div align="center">
<h1>
<a href="#">
<img src="/docs/logo.png" alt="kuery-client-logo" width="400" /><br />
</a>
</h1>
</div>

### Document Site

https://kuery-client.hsbrysk.dev/

## Introduction

By using the following SQL builder, you can easily build and execute SQL.

```kotlin
data class User(...)

class UserRepository(private val kueryClient: KueryClient) {
    fun findById(userId: Int): User? {
        return kueryClient
            .sql { +"SELECT * FROM users WHERE user_id = ${bind(userId)}" }
            .singleOrNull()
    }

    fun search(status: String, vip: Boolean?): List<User> {
        return kueryClient
            .sql {
                +"SELECT * FROM users"
                +"WHERE"
                +"status = ${bind(status)}"
                if (vip != null) {
                    +"vip = ${bind(vip)}"
                }
            }
            .list()
    }

    fun insertMany(users: List<User>): Long {
        return kueryClient
            .sql {
                +"INSERT INTO users (username, email) VALUES"
                +users.joinToString(", ") { "(${bind(it.username)}, ${bind(it.email)})" }
            }
            .rowsUpdated()
    }
}
```

This SQL builder is very simple. There are only two things you need to remember:

- You can concatenate SQL strings using `+`(unaryPlus).
    - You can also directly express logic such as if statements in Kotlin.
- Use the `bind` function for dynamic values.
    - Be careful not to evaluate variables directly as strings, as this will obviously lead to SQL injection.
    - Kuery Client provides Detekt custom rules that detect such dangerous cases.

### Based on spring-data-r2dbc and spring-data-jdbc

Currently, it is implemented based on the well-established `spring-data-r2dbc` and `spring-data-jdbc` in the Java
community. Kuery Client simply provides the aforementioned SQL builder on this foundation.

It is designed to be used alongside both `spring-data-r2dbc` and `spring-data-jdbc`, allowing you to start small.

In the future, we may add a different foundation or possibly create a new one from scratch.

## Getting Started

### Install

#### Gradle

```kotlin
implementation("dev.hsbrysk.kuery-client:kuery-client-spring-data-r2dbc:{{version}}")
// or, implementation("dev.hsbrysk.kuery-client:kuery-client-spring-data-jdbc:{{version}}")
```

#### Maven

```xml

<dependency>
    <groupId>dev.hsbrysk.kuery-client</groupId>
    <artifactId>kuery-client-spring-data-r2dbc</artifactId>
    <!-- or, <artifactId>kuery-client-spring-data-jdbc</artifactId> -->
    <version>{{version}}</version>
</dependency>
```

### Build KueryClient

#### for `kuery-client-spring-data-r2dbc`

```kotlin
val connectionFactory: ConnectionFactory = ...

val kueryClient = SpringR2dbcKueryClient.builder()
    .connectionFactory(connectionFactory)
    .build()
```

#### for `kuery-client-spring-data-jdbc`

```kotlin
val dataSource: DataSource = ...

val kueryClient = SpringJdbcKueryClient.builder()
    .dataSource(dataSource)
    .build()
```

### Let's Use It

```kotlin
val user: User = kueryClient
    .sql { +"SELECT * FROM users WHERE user_id = 1" }
    .singleOrNull()
```
