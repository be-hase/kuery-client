---
description: "Wire Micrometer ObservationRegistry and ObservationConvention; recorded tags, sql_id generation and its constraints, with a Prometheus/Actuator example."
---

# Observation

Kuery Client supports [Micrometer Observation](https://micrometer.io/).

If you want to use this feature, please specify the `ObservationRegistry` when creating the `KueryClient`.

```kotlin {4}
// e.g. In the case of kuery-client-spring-data-r2dbc
val kueryClient = SpringR2dbcKueryClient.builder()
    .connectionFactory(connectionFactory)
    .observationRegistry(...)
    .build()
```

If you want to customize the metrics name or other settings, please implement and specify the `ObservationConvention`
also.

```kotlin {4-5}
// e.g. In the case of kuery-client-spring-data-r2dbc
val kueryClient = SpringR2dbcKueryClient.builder()
    .connectionFactory(connectionFactory)
    .observationRegistry(...)
    .observationConvention(...)
    .build()
```

## Recorded data

Each fetch is recorded as an observation named `kuery.client.fetches` with the following tags:

| Tag | Cardinality | Description |
|---|---|---|
| `sql.id` | low | Identifies the query; derived from the calling class/method by default (see [below](#sql-id)) |
| `sql` | high | The SQL body with placeholders (e.g. `:p0`) — bound values are not included, so sensitive data does not leak. High-cardinality tags are attached to spans, not to metrics |

To change the name or the tags, implement `KueryClientFetchObservationConvention` and pass it to
`observationConvention(...)`.

## Example: spring-boot-starter-actuator & Prometheus

::: info
We won't go into detail about Spring Boot, Micrometer, and Prometheus here.
The documentation is written concisely, assuming you are familiar with these.
:::

First, add `org.springframework.boot:spring-boot-starter-actuator` and `io.micrometer:micrometer-registry-prometheus` as
dependencies.

```kotlin
// ...
// other dependencies
// ...
implementation("org.springframework.boot:spring-boot-starter-actuator")
implementation("io.micrometer:micrometer-registry-prometheus")
```

Then, write the following and register KueryClient as a Bean:

```kotlin
@Configuration(proxyBeanMethods = false)
class ExampleConfiguration {
    @Bean
    fun kueryClient(connectionFactory: ConnectionFactory, observationRegistry: ObservationRegistry): KueryClient {
        return SpringR2dbcKueryClient.builder()
            .connectionFactory(connectionFactory)
            .observationRegistry(observationRegistry)
            .build()
    }
}
```

Suppose you are implementing a repository like the following.

```kotlin
package com.example.spring.data.r2dbc

// ...

@Repository
class UserRepository(private val kueryClient: KueryClient) {
    suspend fun selectByUserId(userId: Int): User? = kueryClient
        .sql {
            +"SELECT * FROM users WHERE user_id = $userId"
        }
        .singleOrNull()
}
```

With these assumptions, you can obtain Prometheus metrics as follows:

```shell
curl {host}/actuator/prometheus | grep kuery

# HELP kuery_client_fetches_active_seconds
# TYPE kuery_client_fetches_active_seconds summary
kuery_client_fetches_active_seconds_count{sql_id="com.example.spring.data.r2dbc.UserRepository.selectByUserId"} 0
kuery_client_fetches_active_seconds_sum{sql_id="com.example.spring.data.r2dbc.UserRepository.selectByUserId"} 0.0
# HELP kuery_client_fetches_active_seconds_max
# TYPE kuery_client_fetches_active_seconds_max gauge
kuery_client_fetches_active_seconds_max{sql_id="com.example.spring.data.r2dbc.UserRepository.selectByUserId"} 0.0
# HELP kuery_client_fetches_seconds
# TYPE kuery_client_fetches_seconds summary
kuery_client_fetches_seconds_count{error="none",sql_id="com.example.spring.data.r2dbc.UserRepository.selectByUserId"} 14
kuery_client_fetches_seconds_sum{error="none",sql_id="com.example.spring.data.r2dbc.UserRepository.selectByUserId"} 0.13953154
# HELP kuery_client_fetches_seconds_max
# TYPE kuery_client_fetches_seconds_max gauge
kuery_client_fetches_seconds_max{error="none",sql_id="com.example.spring.data.r2dbc.UserRepository.selectByUserId"} 0.026267833
```

As shown above, the `sql_id` label is automatically derived from the repository class and method that call
`kueryClient.sql { ... }`.

## `sql_id`

Auto-generation of the `sql_id` is controlled by the builder's `enableAutoSqlIdGeneration(...)` flag. It
defaults to `true` when an `ObservationRegistry` is specified, and `false` otherwise — in which case the fixed
id `NONE` is used.

You can also set the id explicitly per query: `sql("my-sql-id") { ... }`.

### Same method, same id

If you have multiple `kueryClient.sql {...}` calls within the same method, the same `sql_id`
will be used. Therefore, it is recommended to implement one SQL per method in the repository.

```kotlin
@Repository
class UserRepository(private val kueryClient: KueryClient) {
    suspend fun selectByUserId(userId: Int): UserAndDetail {
        val user: User = kueryClient
            .sql {
                +"SELECT * FROM users WHERE user_id = $userId"
            }
            .single()
        val userDetail: UserDetail = kueryClient
            .sql {
                +"SELECT * FROM user_details WHERE user_id = $userId"
            }
            .single()
        return UserAndDetail(user, userDetail)
    }
}
```

If you absolutely need to make multiple calls in a repository method, you can avoid this by specifying the `sql_id`
yourself.

```kotlin
@Repository
class UserRepository(private val kueryClient: KueryClient) {
    suspend fun selectByUserId(userId: Int): UserAndDetail {
        val user: User = kueryClient
            .sql("my_sql_id_1") {
                +"SELECT * FROM users WHERE user_id = $userId"
            }
            .single()
        val userDetail: UserDetail = kueryClient
            .sql("my_sql_id_2") {
                +"SELECT * FROM user_details WHERE user_id = $userId"
            }
            .single()
        return UserAndDetail(user, userDetail)
    }
}
```

## Streaming operations are not observed

Observation is not recorded for the streaming terminal operations:

- `flow()` / `flowMap()` (kuery-client-spring-data-r2dbc)
- `sequence()` / `sequenceMap()` (kuery-client-spring-data-jdbc)

These operations return before the query results are consumed, so there is no obvious point at which the observation
should stop. Both "until the stream terminates" and "until the first element arrives" are reasonable but different
semantics. Until this is settled, these operations simply do not record metrics.

If you need metrics for such a query, use `list()` / `listMap()` instead, or measure the consumption of the stream
yourself.
