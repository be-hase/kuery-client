---
description: Overview of Kuery Client's features, motivation, and the SQL builder with string interpolation (R2DBC / JDBC).
---

# Introduction

::: info Prerequisites
This documentation assumes you are already familiar with Spring / Spring Boot and does not explain them.
:::

## Features

- **Love SQL ♥**
    - ORM libraries are convenient, but they each require learning their own DSL, which we believe is a steep
      cost. Kuery Client emphasizes writing SQL as it is.
- **Based on spring-data-r2dbc and spring-data-jdbc**
    - Kuery Client is implemented on top of spring-data-r2dbc and spring-data-jdbc. Use whichever you prefer.
      You can keep using Spring's ecosystem as is, such as `@Transactional`.
- **Observability**
    - It supports Micrometer Observation, so you can collect and customize metrics, tracing, and logging.
- **Extensible**
    - When dealing with complex data schemas, you often want to share common query logic. Kotlin's extension
      functions make this easy.

## Motivation

We have used numerous ORM libraries, but in the end, we preferred libraries
like [MyBatis](https://github.com/mybatis/mybatis-3) that allow writing SQL directly.

To construct SQL dynamically, custom template syntax (such as if/foreach) is often used, but we prefer to write logic
using the syntax provided by the programming language as much as possible.
We want to write dynamic SQL using Kotlin syntax, similar to [kotlinx.html](https://github.com/Kotlin/kotlinx.html).

To meet these needs, we implemented `Kuery Client`.

## Overview

By using the following SQL builder, you can easily build and execute SQL. Whether using R2DBC or JDBC, the way of
writing is almost the same.

A Kotlin compiler plugin converts string interpolation into parameter binding.

::: code-group

```kotlin [kuery-client-spring-data-r2dbc]
data class User(...)

class UserRepository(private val kueryClient: KueryClient) {
    suspend fun findById(userId: Int): User? = kueryClient
        .sql { +"SELECT * FROM users WHERE user_id = $userId" }
        .singleOrNull()

    suspend fun search(status: String, vip: Boolean?): List<User> = kueryClient
        .sql {
            +"""
            SELECT * FROM users
            WHERE
            status = $status
            """
            if (vip != null) {
                +"AND vip = $vip"
            }
        }
        .list()

    suspend fun insertMany(users: List<User>): Long = kueryClient
        .sql {
            +"INSERT INTO users (username, email)"
            // useful helper function
            values(users) { listOf(it.username, it.email) }
        }
        .rowsUpdated()
}
```

```kotlin [kuery-client-spring-data-jdbc]
data class User(...)

class UserRepository(private val kueryClient: KueryBlockingClient) {
    fun findById(userId: Int): User? = kueryClient
        .sql { +"SELECT * FROM users WHERE user_id = $userId" }
        .singleOrNull()

    fun search(status: String, vip: Boolean?): List<User> = kueryClient
        .sql {
            +"""
            SELECT * FROM users
            WHERE
            status = $status
            """
            if (vip != null) {
                +"AND vip = $vip"
            }
        }
        .list()

    fun insertMany(users: List<User>): Long = kueryClient
        .sql {
            +"INSERT INTO users (username, email)"
            // useful helper function
            values(users) { listOf(it.username, it.email) }
        }
        .rowsUpdated()
}
```

:::

This SQL builder is very simple. There are only two things you need to remember:

- You can concatenate SQL strings using `+`(unaryPlus).
    - You can also directly express logic such as if statements in Kotlin.
- You can bind parameters using string interpolation.

## Based on spring-data-r2dbc and spring-data-jdbc

Currently, it is implemented on top of the well-established `spring-data-r2dbc` and `spring-data-jdbc`.
Kuery Client simply provides the SQL builder shown above on this foundation.

It is designed to be usable alongside plain `spring-data-r2dbc` / `spring-data-jdbc` code, so you can start
small.

In the future, we may add a different foundation or possibly create a new one from scratch.
