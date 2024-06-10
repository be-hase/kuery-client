# Observation

Kuery Client supports [Micrometer Observation](https://micrometer.io/).

If you want to use this feature, please specify the observationRegistry in the builder.

```kotlin
// e.g. kuery-client-spring-data-r2dbc
val kueryClient = SpringR2dbcKueryClient.builder()
    .connectionFactory(connectionFactory)
    .observationRegistry(...) // here
    .build()
```

If you want to customize the metrics name or other settings, please implement and specify the observationConvention
also.

```kotlin
// e.g. kuery-client-spring-data-r2dbc
val kueryClient = SpringR2dbcKueryClient.builder()
    .connectionFactory(connectionFactory)
    .observationRegistry(...) // here
    .observationConvention(...) // here
    .build()
```
