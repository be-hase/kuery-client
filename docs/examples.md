---
description: Run the repository's Spring WebFlux/R2DBC and Spring WebMVC/JDBC example applications against MySQL.
---

# Examples

The repository contains two runnable Spring Boot applications that expose the same REST API:

| Project | Web stack | Kuery Client API |
|---|---|---|
| [`spring-data-r2dbc`](https://github.com/be-hase/kuery-client/tree/main/examples/spring-data-r2dbc) | Spring WebFlux + coroutines | `KueryClient` |
| [`spring-data-jdbc`](https://github.com/be-hase/kuery-client/tree/main/examples/spring-data-jdbc) | Spring WebMVC | `KueryBlockingClient` |

Both demonstrate dynamic SQL, collection binding, generated values, custom converters, programmatic and
declarative transactions, and Micrometer Observation with Prometheus.

## Prerequisites

- Java 17 or later
- Docker with the Compose plugin
- A clone of the Kuery Client repository

The examples use the repository source through Gradle composite builds, so you do not need to publish artifacts
locally.

## Start MySQL

From the repository root:

```shell
cd examples
docker compose up -d
./init_mysql.sh
```

This starts MySQL 8.0.37 on `localhost:13306`; the initialization script waits for it to accept connections,
creates the `testdb` database, and inserts sample users and orders. The script is not idempotent, so run it once
for a newly created container.

## Run an application

Continue from the `examples` directory. Run one application at a time because both listen on port `8080`:

::: code-group

```shell [R2DBC]
../gradlew :spring-data-r2dbc:bootRun
```

```shell [JDBC]
../gradlew :spring-data-jdbc:bootRun
```

:::

Wait until Spring Boot reports that the application has started, then try the API from another terminal.

## Try the API

```shell
# List the two seeded users
curl http://localhost:8080/users

# Fetch one user
curl http://localhost:8080/users/1

# Exercise collection binding with an IN clause
curl 'http://localhost:8080/users?usernames=user1&usernames=user2'

# Fetch a mapped join result
curl http://localhost:8080/users/1/orders

# Inspect Kuery Client metrics
curl http://localhost:8080/actuator/prometheus | grep kuery_client
```

The list request returns data shaped like:

```json
[
  {"userId":1,"username":"user1","email":"user1@example.com"},
  {"userId":2,"username":"user2","email":"user2@example.com"}
]
```

Read the matching `ExampleApplication.kt` to compare the R2DBC and JDBC implementations:

- [R2DBC application](https://github.com/be-hase/kuery-client/blob/main/examples/spring-data-r2dbc/src/main/kotlin/com/example/spring/data/r2dbc/ExampleApplication.kt)
- [JDBC application](https://github.com/be-hase/kuery-client/blob/main/examples/spring-data-jdbc/src/main/kotlin/com/example/spring/data/jdbc/ExampleApplication.kt)

## Stop and reset

Stop the application with <kbd>Ctrl</kbd>+<kbd>C</kbd>, then remove the example container and its data:

```shell
docker compose down
```

Run the start and initialization steps again to get a clean database.
