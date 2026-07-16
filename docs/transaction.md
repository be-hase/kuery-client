---
description: Use Spring transactions with Kuery Client — programmatic (TransactionalOperator / TransactionTemplate) and declarative @Transactional.
---

# Transaction

Kuery Client does not have its own transaction API — you use the transaction mechanisms provided by Spring as
is. This page gives a brief overview; for details, please refer to the
[Spring documentation](https://docs.spring.io/spring-framework/reference/data-access/transaction.html).

## Programmatic Transaction Management

Use `TransactionalOperator` (R2DBC) or `TransactionTemplate` (JDBC) to manage transactions programmatically.

When using Spring Boot, they are registered as beans by default, so you can inject and use them as is. (On the
other hand, if you are using multiple databases, for example, you will need to provide them yourself. In such
cases, please refer to the Spring documentation and set them up accordingly.)

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

Of course, you can also use the AOP-based approach. In this case, add `@Transactional` to the methods where you
want to apply the transaction.

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
