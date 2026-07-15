<div align="center">
<h1>
<a href="https://kuery-client.hsbrysk.dev/"><img src="docs/logo.png" alt="kuery-client-logo" width="400" /></a>
</h1>
<a href="https://central.sonatype.com/search?q=kuery-client"><img alt="Maven Central Version" src="https://img.shields.io/maven-central/v/dev.hsbrysk.kuery-client/kuery-client-core"></a>
<br />
<a href="https://kuery-client.hsbrysk.dev/"><b>Document Site</b></a>
</div>

Kuery Client is a Kotlin/JVM database client for those who want to write SQL. It is built on top of
`spring-data-r2dbc` and `spring-data-jdbc`, and lets you write raw SQL using Kotlin string interpolation —
a Kotlin compiler plugin transparently converts the interpolation into named parameter binding, so it is
safe from SQL injection.

```kotlin
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

## Features

- **Love SQL ♥** — no ORM DSL to learn; write SQL as it is, and express dynamic SQL with plain Kotlin
  syntax (`if`, `for`, ...).
- **Safe** — string interpolation is converted into parameter binding at compile time
  ([Compiler Safety Check](https://kuery-client.hsbrysk.dev/compiler-safety-check)).
- **Based on spring-data-r2dbc and spring-data-jdbc** — use whichever you prefer, together with Spring's
  ecosystem such as `@Transactional`.
- **Observability** — supports Micrometer Observation, so Metrics/Tracing/Logging can be customized.
- **Extensible** — write common query logic with Kotlin extension functions.

## Getting Started

Replace `<version>` with the latest version, which you can find in the Maven Central badge above or on
the [Getting Started](https://kuery-client.hsbrysk.dev/getting-started) page.

```kotlin
plugins {
    id("dev.hsbrysk.kuery-client") version "<version>"
}

dependencies {
    implementation("dev.hsbrysk.kuery-client:kuery-client-spring-data-r2dbc:<version>")
    // or, implementation("dev.hsbrysk.kuery-client:kuery-client-spring-data-jdbc:<version>")
}
```

Build a client from a `ConnectionFactory` (R2DBC) or a `DataSource` (JDBC), and use it:

```kotlin
val kueryClient = SpringR2dbcKueryClient.builder()
    .connectionFactory(connectionFactory)
    .build()

val user: User? = kueryClient
    .sql { +"SELECT * FROM users WHERE user_id = $userId" }
    .singleOrNull()
```

## Documentation

The full documentation — motivation, basics, transactions, type conversion, observation, the compiler
safety check, helpers, and the compatibility matrix — lives on the
[document site](https://kuery-client.hsbrysk.dev/).

## Examples

Runnable example projects live in [examples/](examples):

- [examples/spring-data-r2dbc](examples/spring-data-r2dbc)
- [examples/spring-data-jdbc](examples/spring-data-jdbc)
