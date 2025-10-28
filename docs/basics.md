# Basics

## Building SQL

### `+`(unaryPlus)

Concatenate SQL strings using the + operator.

```kotlin
kueryClient
    .sql {
        +"SELECT * FROM users"
        +"WHERE user_id = 1"
    }
```

Of course, if there is no need to concatenate, you don't have to.

```kotlin
kueryClient
    .sql {
        +"""
        SELECT * FROM users
        WHERE user_id = 1
        """
    }
```

### `fun add(sql: String)`

It is an alias for `+`(unaryPlus). However, since the argument is annotated
with `org.intellij.lang.annotations.Language`, if you are using a JetBrains IDE, you will get syntax assistance.

## Binding Parameters

When you want to bind parameters, use string interpolation.

```kotlin
val userId = "..."
kueryClient
    .sql {
        +"""
        SELECT * FROM users
        WHERE user_id = $userId
        """
    }
```

## Logic such as `if` and `for` ...etc

Just write using Kotlin syntax. There is no need to learn special syntax.

```kotlin
kueryClient
    .sql {
        +"SELECT * FROM users"
        +"WHERE"
        +"status = $status"
        if (vip != null) {
            +"AND vip = $vip"
        }
    }
```

## Fetch Result

`kuery-client-spring-data-r2dbc/jdbc` both have a minimal interface. In the case of `kuery-client-spring-data-r2dbc`, it
will be a suspend function.

### (suspend) fun singleMap(): Map<String, Any?>

Receives the results as a map.

```kotlin
val map: Map<String, Any?> = kueyClient
    .sql { +"SELECT * FROM users WHERE user_id = 1" }
    .singleMap()
```

### `(suspend) fun singleMapOrNull(): Map<String, Any?>?`

Receives the results as a map.

```kotlin
val map: Map<String, Any?>? = kueyClient
    .sql { +"SELECT * FROM users WHERE user_id = 1" }
    .singleMapOrNull()
```

### `(suspend) fun <T : Any> single(returnType: KClass<T>): T`

Receives the results converted to the specified type.

```kotlin
val user: User = kueyClient
    .sql { +"SELECT * FROM users WHERE user_id = 1" }
    .single()
```

### `(suspend) fun <T : Any> singleOrNull(returnType: KClass<T>): T?`

Receives the results converted to the specified type.

```kotlin
val user: User? = kueyClient
    .sql { +"SELECT * FROM users WHERE user_id = 1" }
    .singleOrNull()
```

### `(suspend) fun listMap(): List<Map<String, Any?>>`

Receives the results of multiple rows as a map.

```kotlin
val result: List<Map<String, Any?>> = kueyClient
    .sql { +"SELECT * FROM users WHERE user_id = 1" }
    .listMap()
```

### `(suspend) fun <T : Any> list(returnType: KClass<T>): List<T>`

Receives the results of multiple rows converted to the specified type.

```kotlin
val users: List<User> = kueyClient
    .sql { +"SELECT * FROM users WHERE user_id = 1" }
    .list()
```

### [`kuery-client-spring-data-r2dbc` only] `fun flowMap(): Flow<Map<String, Any?>>`

Receives the results of multiple rows as a map.

```kotlin
val result: Flow<Map<String, Any?>> = kueyClient
    .sql { +"SELECT * FROM users WHERE user_id = 1" }
    .flowMap()
```

### [`kuery-client-spring-data-r2dbc` only] `fun <T : Any> flow(returnType: KClass<T>): Flow<T>`

Receives the results of multiple rows converted to the specified type.

```kotlin
val users: Flow<User> = kueyClient
    .sql { +"SELECT * FROM users WHERE user_id = 1" }
    .flow()
```

### `(suspend) fun rowsUpdated(): Long`

Contract for fetching the number of affected rows

```kotlin
val result: Long = kueyClient
    .sql {+"INSERT INTO users (username, email) VALUES ('username1', 'email1')"}
    .rowsUpdated()
```

### `(suspend) fun generatedValues(vararg columns: String): Map<String, Any>`

Receives the values generated on the database side. For example, an auto increment value.

```kotlin
val result: Map<String, Any> = kueyClient
    .sql {+"INSERT INTO users (username, email) VALUES ('username1', 'email1')"}
    .generatedValues("user_id")
```
