<div align="center">
<h1>
<a href="https://kuery-client.hsbrysk.dev/">
<img src="/docs/logo.png" alt="kuery-client-logo" width="400" /><br />
</a>
</h1>
<img alt="Maven Central Version" src="https://img.shields.io/maven-central/v/dev.hsbrysk.kuery-client/kuery-client-core">
<br />
<a href="https://kuery-client.hsbrysk.dev/"><b>Document Site</b></a>
</div>

## Introduction

### Features

- **Love SQL ♥**
    - While ORM libraries in the world are convenient, they often require learning their own DSL, which I believe has a
      high learning cost. Kuery Client emphasizes writing SQL as it is.
- **Based on spring-data-r2dbc and spring-data-jdbc**
    - Kuery Client is implemented based on spring-data-r2dbc and spring-data-jdbc. Use whichever you prefer. You can use
      Spring's ecosystem as it is, such as `@Transactional`.
- **Observability**
    - It supports Micrometer Observation, so Metrics/Tracing/Logging can also be customized.
- **Highly extensible**
    - When dealing with complex data schemas, there are often cases where you want to write common query logic. Thanks
      to Kotlin's extension functions, this becomes easier.


### Motivation

I have used numerous ORM libraries, but in the end, I preferred libraries like MyBatis that allow writing SQL directly.

To construct SQL dynamically, custom template syntax (such as if/foreach) is often used, but I prefer to write logic
using the syntax provided by the programming language as much as possible.
I want to write dynamic SQL using Kotlin syntax, similar to [kotlinx.html](https://github.com/Kotlin/kotlinx.html).

To meet these needs, I implemented Kuery Client.

### Overview

By using the following SQL builder, you can easily build and execute SQL. Whether using R2DBC or JDBC, the way of
writing is almost the same.

```kotlin
data class User(...)

class UserRepository(private val kueryClient: KueryClient) {
    suspend fun findById(userId: Int): User? {
        return kueryClient
            .sql { +"SELECT * FROM users WHERE user_id = ${bind(userId)}" }
            .singleOrNull()
    }

    suspend fun search(status: String, vip: Boolean?): List<User> {
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

    suspend fun insertMany(users: List<User>): Long {
        return kueryClient
            .sql {
                +"INSERT INTO users (username, email) VALUES"
                +values(users) { listOf(it.username, it.email) }
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
    - Kuery Client provides [Detekt custom rules](/docs/detekt) that detect such dangerous cases.

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
