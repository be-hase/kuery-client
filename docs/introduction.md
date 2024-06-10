# Introduction

By using the following DSL, you can easily construct and execute SQL.

This DSL is very simple. There are only two things you need to remember:

- You can concatenate SQL strings using `+` (unaryPlus). You can also easily incorporate logic with if statements.
- Use the bind function for dynamic variables.
    - Be careful not to evaluate variables directly as strings, as this will obviously lead to SQL injection.
    - Kuery client provides Detekt custom rules that detect such dangerous cases.

```kotlin
data class User(...)

fun findById(userId: Int): User? {
    return kueryClient
        .sql { +"SELECT * FROM users WHERE user_id = ${bind(userId)}" }
        .singleOrNull()
}

fun search(status: String, vip: Boolean?): List<User> {
    return kueryClient
        .sql {
            +"SELECT * FROM users"
            +"WHERE"
            +"status = ${bind(status)}"
            if (vip != null) {
                +"vip = ${bind(vip)}"
            }
        }
        .list()
}

fun insertMany(users: List<User>): Long {
    return kueryClient
        .sql {
            +"INSERT INTO users (username, email) VALUES"
            +users.joinToString(", ") { "(${bind(it.username)}, ${bind(it.email)})" }
        }
        .rowsUpdated()
}
```

## Based on spring-data-r2dbc and spring-data-jdbc

Currently, it is implemented based on the well-established `spring-data-r2dbc` and `spring-data-jdbc` in the Java
community. Kuery client simply provides the aforementioned DSL on this foundation.

It is designed to be used alongside both spring-data-r2dbc and spring-data-jdbc, allowing you to start small.
