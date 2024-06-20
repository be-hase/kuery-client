# Introduction

## Features

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

## Motivation

I have used numerous ORM libraries, but in the end, I preferred libraries like MyBatis that allow writing SQL directly.

To construct SQL dynamically, custom template syntax (such as if/foreach) is often used, but I prefer to write logic
using the syntax provided by the programming language as much as possible.
I want to write dynamic SQL using Kotlin syntax, similar to [kotlinx.html](https://github.com/Kotlin/kotlinx.html).

To meet these needs, I implemented Kuery Client.

## Overview

By using the following SQL builder, you can easily build and execute SQL. Whether using R2DBC or JDBC, the way of
writing is almost the same.

::: code-group

```kotlin [kuery-client-spring-data-r2dbc]
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

```kotlin [kuery-client-spring-data-jdbc]
data class User(...)

class UserRepository(private val kueryClient: KueryBlockingClient) {
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
                +values(users) { listOf(it.username, it.email) }
            }
            .rowsUpdated()
    }
}
```

:::

This SQL builder is very simple. There are only two things you need to remember:

- You can concatenate SQL strings using `+`(unaryPlus).
    - You can also directly express logic such as if statements in Kotlin.
- Use the `bind` function for dynamic values.
    - Be careful not to evaluate variables directly as strings, as this will obviously lead to SQL injection.
    - Kuery Client provides [Detekt custom rules](/detekt) that detect such dangerous cases.

## Based on spring-data-r2dbc and spring-data-jdbc

Currently, it is implemented based on the well-established `spring-data-r2dbc` and `spring-data-jdbc` in the Java
community. Kuery Client simply provides the aforementioned SQL builder on this foundation.

It is designed to be used alongside both `spring-data-r2dbc` and `spring-data-jdbc`, allowing you to start small.

In the future, we may add a different foundation or possibly create a new one from scratch.

## Preface

This document does not explain Spring or Spring Boot. It is written assuming you are already familiar with them. For
those without this knowledge, the document may be difficult to understand.
