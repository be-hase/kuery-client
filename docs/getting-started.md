---
description: Add Kuery Client to an existing Kotlin/Spring Boot project, choose R2DBC or JDBC, configure the client, and run the first query.
---

# Getting Started

This guide adds Kuery Client to an existing Kotlin/Spring Boot project. If you would rather start from a complete
application, clone the repository and follow the [runnable Examples](/examples).

## Requirements

- Java 17 or later
- Gradle 8.4 or later
- An existing Kotlin/JVM Spring project
- A Kotlin, Spring Boot, and Spring Data version compatible with the Kuery Client release; see
  [Compatibility](/compatibility)

Parameter binding relies on the Kuery Client Kotlin compiler plugin, which is applied through its Gradle plugin.
Maven, Kotlin/JS, Kotlin/Native, and Kotlin/Wasm are not currently supported.

## Choose R2DBC or JDBC

| | R2DBC | JDBC |
|---|---|---|
| Client type | `KueryClient` | `KueryBlockingClient` |
| Regular terminal operations | suspending | blocking |
| Streaming | cold coroutine `Flow` | eager, resource-backed `CloseableSequence` |
| Statement options | `fetchSize` | `fetchSize`, `maxRows`, `queryTimeoutSeconds` |

Apart from these execution and streaming differences, SQL construction and typed row mapping use the same API.

## Install

The following examples use MySQL. Add the Kuery Client plugin, the matching Spring Boot starter, Kuery Client
module, and database driver to the module that contains your repository code.

::: code-group

```kotlin [R2DBC · build.gradle.kts]
plugins {
    id("dev.hsbrysk.kuery-client") version "{{version}}"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("dev.hsbrysk.kuery-client:kuery-client-spring-data-r2dbc:{{version}}")
    runtimeOnly("io.asyncer:r2dbc-mysql")
}
```

```kotlin [JDBC · build.gradle.kts]
plugins {
    id("dev.hsbrysk.kuery-client") version "{{version}}"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("dev.hsbrysk.kuery-client:kuery-client-spring-data-jdbc:{{version}}")
    runtimeOnly("com.mysql:mysql-connector-j")
}
```

:::

Kuery Client does not provide a Spring Boot starter and does not bring in a database driver. For PostgreSQL or
H2 driver choices, see [Supported Platforms](/supported-platforms).

::: warning Multi-module projects
Apply `dev.hsbrysk.kuery-client` to every module that contains `sql { ... }` calls. Applying it only to the
application module does not transform repository code compiled in another module.
:::

::: details Runtime error: `SqlBuilder.add` / `String.unaryPlus` must be rewritten
This means the Gradle plugin was not applied to the module that compiled the call. `+"..."` / `add(...)` are
intentionally broken without the compiler plugin; otherwise interpolation could be executed as raw SQL. Apply
the plugin as shown above, then clean and rebuild the affected module.
:::

### Recommended compiler settings

For a new project, enable the settings below. They remain opt-in because they were introduced after
Kuery Client's initial releases, but they are intended to become the defaults in a future release.
This guide uses MySQL; replace `mysql` with the project's dialect when needed:

```kotlin
kueryClient {
    autoTrimIndent = true
    sqlSyntaxCheck = "mysql"
    strict = true
}
```

Dialect validation can report false positives for uncommon vendor-specific SQL. See
[Configuration](/configuration) before enabling these options in an existing codebase.

## Configure the database

Configure Spring Boot as usual. For a local MySQL instance listening on port `3306`:

::: code-group

```yaml [R2DBC · application.yml]
spring:
  r2dbc:
    url: r2dbc:mysql://localhost:3306/app
    username: app
    password: secret
```

```yaml [JDBC · application.yml]
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/app
    username: app
    password: secret
```

:::

This guide assumes a table created by your preferred migration tool:

```sql
CREATE TABLE users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL
);
```

## Register the client

Kuery Client deliberately has no auto-configuration. Register one shared client using Spring Boot's
auto-configured `ConnectionFactory` or `DataSource`:

::: code-group

```kotlin [R2DBC]
@Configuration(proxyBeanMethods = false)
class KueryClientConfiguration {
    @Bean
    fun kueryClient(connectionFactory: ConnectionFactory): KueryClient =
        SpringR2dbcKueryClient.builder()
            .connectionFactory(connectionFactory)
            .build()
}
```

```kotlin [JDBC]
@Configuration(proxyBeanMethods = false)
class KueryClientConfiguration {
    @Bean
    fun kueryClient(dataSource: DataSource): KueryBlockingClient =
        SpringJdbcKueryClient.builder()
            .dataSource(dataSource)
            .build()
}
```

:::

## Run the first query

The compiler plugin converts `$userId` into a named bind parameter. The database receives SQL equivalent to
`WHERE user_id = :p0`; the value is never concatenated into the SQL text.

::: code-group

```kotlin [R2DBC]
data class User(
    val userId: Int,
    val username: String,
)

@Repository
class UserRepository(private val kueryClient: KueryClient) {
    suspend fun findById(userId: Int): User? = kueryClient
        .sql { +"SELECT user_id, username FROM users WHERE user_id = $userId" }
        .singleOrNull()
}
```

```kotlin [JDBC]
data class User(
    val userId: Int,
    val username: String,
)

@Repository
class UserRepository(private val kueryClient: KueryBlockingClient) {
    fun findById(userId: Int): User? = kueryClient
        .sql { +"SELECT user_id, username FROM users WHERE user_id = $userId" }
        .singleOrNull()
}
```

:::

Next, read [Building SQL](/basics), [Fetching Results](/fetching-results), and [Row Mapping](/row-mapping).
