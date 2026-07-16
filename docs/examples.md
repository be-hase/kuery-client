---
description: "Links to runnable example projects: Spring WebFlux + R2DBC and Spring WebMVC + JDBC."
---

# Examples

The repository contains runnable Spring Boot applications. Both implement the same small REST API (CRUD for a
`users` table) and demonstrate:

- Registering `KueryClient` / `KueryBlockingClient` as a bean
- Repositories built with the SQL builder, including dynamic queries
- Custom type converters (a wrapper data class mapped with `@WritingConverter` / `@ReadingConverter`)
- Transactions, both programmatic and `@Transactional`
- Micrometer Observation with Prometheus metrics via Spring Boot Actuator

## Spring WebFlux and `kuery-client-spring-data-r2dbc`

https://github.com/be-hase/kuery-client/tree/main/examples/spring-data-r2dbc

A coroutine-based application using `KueryClient`.

## Spring WebMVC and `kuery-client-spring-data-jdbc`

https://github.com/be-hase/kuery-client/tree/main/examples/spring-data-jdbc

A blocking application using `KueryBlockingClient`.
