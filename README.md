<div align="center">
<h1>
<a href="https://kuery-client.hsbrysk.dev/"><img src="docs/public/logo.png" alt="kuery-client-logo" width="400" /></a>
</h1>

**Write SQL as it is.**

A Kotlin SQL client built on Spring Data — string interpolation becomes bind parameters via a compiler plugin.

<a href="https://central.sonatype.com/search?q=kuery-client"><img alt="Maven Central Version" src="https://img.shields.io/maven-central/v/dev.hsbrysk.kuery-client/kuery-client-core"></a>
<a href="https://github.com/be-hase/kuery-client/actions/workflows/ci.yml"><img alt="CI" src="https://github.com/be-hase/kuery-client/actions/workflows/ci.yml/badge.svg"></a>
<a href="LICENSE"><img alt="License" src="https://img.shields.io/github/license/be-hase/kuery-client"></a>

<a href="https://kuery-client.hsbrysk.dev/"><b>Document Site</b></a> ·
<a href="https://kuery-client.hsbrysk.dev/getting-started"><b>Getting Started</b></a> ·
<a href="examples"><b>Examples</b></a>
</div>

---

It looks like plain string interpolation...

```kotlin
val user: User? = kueryClient
    .sql { +"SELECT * FROM users WHERE user_id = $userId" }
    .singleOrNull()
```

...but it executes as a **parameterized query**. A Kotlin compiler plugin rewrites the
interpolation into named parameter binding at compile time — so you get the readability of
raw SQL without the risk of SQL injection:

```sql
SELECT * FROM users WHERE user_id = :p0  -- :p0 = userId, bound as a named parameter
```

## Why Kuery Client?

- ♥️ **Love SQL**
    - ORM libraries are convenient, but they each require learning their own DSL, which we believe is a steep
      cost. Kuery Client emphasizes writing SQL as it is.
- 🛡️ **Safe by design**
    - String interpolation is converted into bind parameters by the compiler plugin — never concatenated into
      the SQL text. A built-in [compiler safety check](https://kuery-client.hsbrysk.dev/compiler-safety-check)
      warns when it detects a SQL string that cannot be converted safely.
- 🍃 **Based on spring-data-r2dbc and spring-data-jdbc**
    - Kuery Client is implemented on top of spring-data-r2dbc (coroutines) and spring-data-jdbc (blocking).
      Use whichever you prefer. You can keep using Spring's ecosystem as is, such as `@Transactional`.
- 🔭 **Observability**
    - It supports Micrometer Observation, so you can collect and customize metrics, tracing, and logging.
- 🧩 **Extensible**
    - When dealing with complex data schemas, you often want to share common query logic. Kotlin's extension
      functions make this easy.

## A Taste of the DSL

Dynamic SQL is just Kotlin — `if` / `when` / loops, no template syntax to learn.
Whether using R2DBC or JDBC, the way of writing is almost the same.

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

There are only two things you need to remember:

- Concatenate SQL strings using `+` (unaryPlus). Express logic such as `if` directly in Kotlin.
- Bind parameters using string interpolation.

## Motivation

We have used numerous ORM libraries, but in the end, we preferred libraries
like [MyBatis](https://github.com/mybatis/mybatis-3) that allow writing SQL directly.

To construct SQL dynamically, custom template syntax (such as if/foreach) is often used, but we prefer to write logic
using the syntax provided by the programming language as much as possible.
We want to write dynamic SQL using Kotlin syntax, similar to [kotlinx.html](https://github.com/Kotlin/kotlinx.html).

To meet these needs, we implemented `Kuery Client`.

Kuery Client simply provides the SQL builder shown above on top of the well-established
`spring-data-r2dbc` / `spring-data-jdbc`. It is designed to be usable alongside plain
spring-data code, so you can start small.

## Getting Started

Kuery Client requires Java 17 or later.

### Install

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

### Build KueryClient

For `kuery-client-spring-data-r2dbc`:

```kotlin
val connectionFactory: ConnectionFactory = ...

val kueryClient = SpringR2dbcKueryClient.builder()
    .connectionFactory(connectionFactory)
    .build()
```

For `kuery-client-spring-data-jdbc`:

```kotlin
val dataSource: DataSource = ...

val kueryClient = SpringJdbcKueryClient.builder()
    .dataSource(dataSource)
    .build()
```

### Let's Use It

```kotlin
val userId = "..."
val user: User? = kueryClient
    .sql { +"SELECT * FROM users WHERE user_id = $userId" }
    .singleOrNull()
```

## Documentation

More details are on the [document site](https://kuery-client.hsbrysk.dev/):

- [Basics](https://kuery-client.hsbrysk.dev/basics) — the SQL builder, dynamic SQL, fetch operations
- [Compiler Safety Check](https://kuery-client.hsbrysk.dev/compiler-safety-check) — how unsafe SQL strings are caught at compile time
- [Transaction](https://kuery-client.hsbrysk.dev/transaction) / [Type Conversion](https://kuery-client.hsbrysk.dev/type-conversion) / [Row Mapping](https://kuery-client.hsbrysk.dev/row-mapping)
- [Observation](https://kuery-client.hsbrysk.dev/observation) — Micrometer-based metrics, tracing, and logging
- [Compatibility](https://kuery-client.hsbrysk.dev/compatibility) — supported Kotlin / Spring versions

## Examples

Runnable example projects live in [examples/](examples):

- [examples/spring-data-r2dbc](examples/spring-data-r2dbc)
- [examples/spring-data-jdbc](examples/spring-data-jdbc)

## License

[MIT](LICENSE)
