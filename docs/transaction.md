# Transaction

You can use the transaction mechanisms provided by Spring.

This document provides a brief explanation. For details, please refer to the Spring documentation.

## R2DBC (kuery-client-spring-data-r2dbc)

### Programmatic Transaction Management

When using R2DBC, you can use `TransactionalOperator` to programmatically manage transactions.

When using Spring Boot, it is registered as a bean by default, so you can use it as is.

(On the other hand, if you are using multiple databases, for example, you will need to provide it yourself. In such
cases, please refer to the Spring documentation and set it up accordingly.)

#### Example

```kotlin
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
class UserRepository(private val client: KueryClient) {
    suspend fun insert(
        username: String,
        email: Email,
    ): Int {
        // ...
    }
}
```

### AOP(`@Transactional`) Transaction Management

Of course, you can also use the AOP-based approach. In this case, add `@Transactional` to the methods where you want to
apply the transaction.

```kotlin
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
class UserRepository(private val client: KueryClient) {
    suspend fun insert(
        username: String,
        email: Email,
    ): Int {
        // ...
    }
}
```

## JDBC (kuery-client-spring-data-jdbc)

When using JDBC, you can use `TransactionTemplate` to programmatically manage transactions.

When using Spring Boot, it is registered as a bean by default, so you can use it as is.

(On the other hand, if you are using multiple databases, for example, you will need to provide it yourself. In such
cases, please refer to the Spring documentation and set it up accordingly.)

### Programmatic Transaction Management

#### Example

```kotlin
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
class UserRepository(private val client: KueryClient) {
    fun insert(
        username: String,
        email: Email,
    ): Int {
        // ...
    }
}
```

### AOP(@Transactional) Transaction Management

Of course, you can also use the AOP-based approach. In this case, add `@Transactional` to the methods where you want to
apply the transaction.

```kotlin
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
class UserRepository(private val client: KueryClient) {
    fun insert(
        username: String,
        email: Email,
    ): Int {
        // ...
    }
}
```

## Details

For more details, please refer to the Spring documentation.

https://docs.spring.io/spring-framework/reference/data-access/transaction.html
