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
implementation("org.springframework.boot:spring-boot-starter-actuator:{{version}}")
implementation("io.micrometer:micrometer-registry-prometheus:{{version}}")
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
            .converters(
                listOf(
                    EmailToStringConverter(),
                    StringToEmailConverter(),
                ),
            )
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

Metrics are recorded along with the controller/method where the repository implementing sql_id is used.

## Constraints on `sql_id`

There is a constraint that if you have multiple `kueryClient.sql {...}` calls within the same method, the same `sql_id`
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
