---
description: Use Spring transactions with Kuery Client — programmatic (TransactionalOperator / TransactionTemplate) and declarative @Transactional.
---

# Transaction

Kuery Client does not have its own transaction API — you use the transaction mechanisms provided by Spring as
is. This page gives a brief overview; for details, please refer to the
[Spring documentation](https://docs.spring.io/spring-framework/reference/data-access/transaction.html).

## Programmatic Transaction Management

Use `TransactionalOperator` (R2DBC) or `TransactionTemplate` (JDBC) to manage transactions programmatically.

With Spring Boot's matching data starter, these transaction helpers are normally available as beans. Applications
with multiple databases or custom transaction managers must select and configure the appropriate manager
explicitly.

::: code-group

```kotlin [kuery-client-spring-data-r2dbc]
@Service
class UserService(
    private val userRepository: UserRepository,
    private val transaction: TransactionalOperator, // registered as a bean
) {
    suspend fun addUser(
        username: String,
        email: Email,
    ): Int {
        // Programmatically apply transactions
        return transaction.executeAndAwait {
            userRepository.insert(username, email)
        }
    }
}

@Repository
class UserRepository(private val kueryClient: KueryClient) {
    suspend fun insert(
        username: String,
        email: Email,
    ): Int {
        // ...
    }
}
```

```kotlin [kuery-client-spring-data-jdbc]
@Service
class UserService(
    private val userRepository: UserRepository,
    private val transaction: TransactionTemplate, // registered as a bean
) {
    fun addUser(
        username: String,
        email: Email,
    ): Int {
        // Programmatically apply transactions
        return transaction.execute {
            userRepository.insert(username, email)
        }!!
    }
}

@Repository
class UserRepository(private val kueryClient: KueryBlockingClient) {
    fun insert(
        username: String,
        email: Email,
    ): Int {
        // ...
    }
}
```

:::

## Declarative (`@Transactional`) Transaction Management

For declarative transaction management, add `@Transactional` to a Spring-managed method. Standard Spring proxy
rules still apply—for example, self-invocation does not pass through the transactional proxy.

::: code-group

```kotlin [kuery-client-spring-data-r2dbc]
@Service
class UserService(
    private val userRepository: UserRepository,
) {
    // Apply transactions using AOP
    @Transactional
    suspend fun addUser(
        username: String,
        email: Email,
    ): Int {
        return userRepository.insert(username, email)
    }
}

@Repository
class UserRepository(private val kueryClient: KueryClient) {
    suspend fun insert(
        username: String,
        email: Email,
    ): Int {
        // ...
    }
}
```

```kotlin [kuery-client-spring-data-jdbc]
@Service
class UserService(
    private val userRepository: UserRepository,
) {
    // Apply transactions using AOP
    @Transactional
    fun addUser(
        username: String,
        email: Email,
    ): Int {
        return userRepository.insert(username, email)
    }
}

@Repository
class UserRepository(private val kueryClient: KueryBlockingClient) {
    fun insert(
        username: String,
        email: Email,
    ): Int {
        // ...
    }
}
```

:::

## Streaming inside a transaction

R2DBC `flow()` executes when collected, so collect it inside the reactive transaction that should own the query.
JDBC `sequence()` opens a `ResultSet` immediately and must be consumed or closed inside the active transaction.
See [Fetching Results](/fetching-results#streaming-with-jdbc) for the resource-management rules.
