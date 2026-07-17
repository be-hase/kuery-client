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

The id is derived at compile time by the compiler plugin: every `sql { ... }` call site is rewritten to carry
the fully qualified name of its enclosing declaration (e.g. `com.example.UserRepository.selectByUserId`), so no
stack inspection happens at runtime and a given call site always produces the same id. Calls inside lambdas
fold into the enclosing named method.

The id is attached only when the block is written literally at the call site — a lambda or a function
reference. Everything else resolves to the fixed id `NONE`: a block passed via a variable, a function
reference that needs adaptation (the referenced function declares default arguments or varargs), call sites
that are not compiled with the compiler plugin (e.g. Java callers or reflective invocations), and so on.
Specify the id explicitly in such cases.

A few sharing rules follow from "the id is the enclosing declaration's FQN": overloads of the same method
share one id, as do same-named top-level functions in different files of the same package; and a call inside
an `inline` function uses the inline function's own FQN, not its caller's.

Whether the derived id is actually used is controlled by the builder's `enableAutoSqlIdGeneration(...)` flag.
It defaults to `true` when an `ObservationRegistry` is specified, and `false` otherwise — in which case the
fixed id `NONE` is used.

You can also set the id explicitly per query: `sql("my-sql-id") { ... }`.

### Multiple calls in one method

If a single method contains multiple `kueryClient.sql {...}` calls, each call site gets its own id: a `#N`
suffix is appended in source order.

```kotlin
@Repository
class UserRepository(private val kueryClient: KueryClient) {
    suspend fun selectByUserId(userId: Int): UserAndDetail {
        val user: User = kueryClient
            .sql {
                // sql_id: com.example.UserRepository.selectByUserId#1
                +"SELECT * FROM users WHERE user_id = $userId"
            }
            .single()
        val userDetail: UserDetail = kueryClient
            .sql {
                // sql_id: com.example.UserRepository.selectByUserId#2
                +"SELECT * FROM user_details WHERE user_id = $userId"
            }
            .single()
        return UserAndDetail(user, userDetail)
    }
}
```

A method with a single call keeps the plain id without the suffix. Note that this means adding a second call
to a method changes the first call's id from `...selectByUserId` to `...selectByUserId#1`; specify the
`sql_id` explicitly if you want ids that are independent of such refactorings.

The suffix is an ordinal among the auto-generated ids that would otherwise collide — not the call's position
in the method. Calls with an explicit `sql("...")` id neither receive nor shift numbers: in a method with one
explicit-id call followed by one auto-id call, the auto id stays plain (no `#2`), exactly as if the explicit
call were not there.

```kotlin
val user: User = kueryClient
    .sql("my_sql_id_1") {
        +"SELECT * FROM users WHERE user_id = $userId"
    }
    .single()
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
