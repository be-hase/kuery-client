---
description: "How query results are mapped to Kotlin types: single-column scalars, data classes via constructor mapping (snake_case to camelCase), enums, nullability, value class limitations, and raw maps."
---

# Row Mapping

When you fetch results with a typed terminal operation such as `single()`, `list()`, `flow()`, or `sequence()`,
Kuery Client converts each row into the specified type. There are two mapping strategies, chosen automatically
based on the return type.

## Data classes

Typically, you will map rows to a data class. Each row is mapped with Spring's `DataClassRowMapper`
([spring-r2dbc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/r2dbc/core/DataClassRowMapper.html)
/ [spring-jdbc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/jdbc/core/DataClassRowMapper.html),
respectively): the constructor of the target class is invoked, and each constructor parameter is populated from
the column with the matching name. A `snake_case` column name matches a `camelCase` parameter name.

```kotlin
data class User(
    val userId: Int,      // <- user_id column
    val username: String, // <- username column
    val email: String?,   // <- email column (nullable)
)

val users: List<User> = kueryClient
    .sql { +"SELECT user_id, username, email FROM users" }
    .list()
```

Declare a property as nullable when the column can be `NULL`. If a `NULL` value ends up in a non-nullable
parameter, an exception is thrown at runtime.

## Simple types

If the return type is a simple value type, select one column and its value is converted directly to the target
type. JDBC rejects a scalar query with multiple columns; R2DBC reads the first column. Selecting exactly one
column is therefore required for portable behavior. Whether a type is "simple" is determined by Spring's
[`BeanUtils.isSimpleProperty`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/beans/BeanUtils.html#isSimpleProperty(java.lang.Class)),
which covers primitives and their wrappers, `String`, enums, `Number` types, date/time types, `UUID`, and other
JDK value types.

```kotlin
val count: Long = kueryClient
    .sql { +"SELECT COUNT(*) FROM users" }
    .single()

val usernames: List<String> = kueryClient
    .sql { +"SELECT username FROM users" }
    .list()
```

::: info SQL NULL in multi-row results
When fetching a simple scalar type with `list()` / `flow()` / `sequence()`, a SQL `NULL` is kept as a `null`
element even though this cannot be expressed in the `List<T>` / `Flow<T>` type. Filter or handle nulls yourself
if the column is nullable.
:::

## Enums

An enum column is read by its name by default:

```kotlin
enum class UserStatus { ACTIVE, INACTIVE }

data class User(
    val userId: Int,
    val status: UserStatus, // 'ACTIVE' / 'INACTIVE' in the status column
)
```

Enums are also written by name when used as bind parameters. See
[Binding Parameters](/basics#binding-parameters).

If you want a different representation (for example, a numeric code), register custom converters. See
[Type Conversion](/type-conversion).

## Custom types

Conversion always happens per column, through Spring's `ConversionService`. Any `@ReadingConverter` you
register participates in it — typically when a column value is mapped into a data class property of your custom
type. See [Type Conversion](/type-conversion).

::: warning Custom types cannot be the return type itself
The mapping strategy is chosen solely by whether the return type is a simple value type, regardless of
registered converters. So a custom type as the return type (e.g. `single<StringWrapper>()`) does not go through
your `@ReadingConverter` — it goes down the constructor-mapping path and fails unless the column names happen to
match. Receive custom types as properties of a data class instead.
:::

## Value classes

Kotlin value classes are not supported on the fetch side, in either position:

- As the return type itself (e.g. `single<UserName>()`): `DataClassRowMapper` cannot resolve the
  parameter names of the value class's constructor and fails at runtime.
- As a data class property: instantiation fails because the enclosing class's JVM constructor takes
  the unboxed underlying type. Registering a `@ReadingConverter` for the value class does not help.

```kotlin
@JvmInline
value class UserName(val value: String)

data class User(
    val userId: Int,
    val username: UserName, // NG: fails at runtime
)
```

Fetch the underlying type (or a regular data class) and wrap it yourself.

## Raw maps

If you don't need typed mapping, `singleMap()` / `listMap()` / `flowMap()` / `sequenceMap()` return each row as
a `Map<String, Any?>` keyed by column name.

```kotlin
val rows: List<Map<String, Any?>> = kueryClient
    .sql { +"SELECT * FROM users" }
    .listMap()
```

Alias duplicate column labels in joins. JDBC and R2DBC resolve duplicate map keys differently; see
[Raw maps and column labels](/fetching-results#raw-maps-and-column-labels).
