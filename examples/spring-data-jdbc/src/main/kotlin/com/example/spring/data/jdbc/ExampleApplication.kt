package com.example.spring.data.jdbc

import dev.hsbrysk.kuery.core.KueryBlockingClient
import dev.hsbrysk.kuery.core.list
import dev.hsbrysk.kuery.core.singleOrNull
import dev.hsbrysk.kuery.spring.jdbc.SpringJdbcKueryClient
import io.micrometer.observation.ObservationRegistry
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import javax.sql.DataSource
import kotlin.random.Random

fun main(args: Array<String>) {
    runApplication<ExampleApplication>(*args)
}

@SpringBootApplication
class ExampleApplication

@Configuration(proxyBeanMethods = false)
class ExampleConfiguration {
    @Bean
    fun kueryClient(
        dataSource: DataSource,
        observationRegistry: ObservationRegistry,
    ): KueryBlockingClient {
        return SpringJdbcKueryClient.builder()
            .dataSource(dataSource)
            .observationRegistry(observationRegistry)
            .converters(
                listOf(
                    EmailToStringConverter(),
                    StringToEmailConverter(),
                ),
            )
            .build()
    }
}

data class User(
    val userId: Int,
    val username: String,
    val email: Email,
)

data class UserOrder(
    val username: String,
    val orderId: Int,
    val orderDate: LocalDate,
    val amount: BigDecimal,
)

data class Email(val value: String)

class EmailToStringConverter : Converter<Email, String> {
    override fun convert(source: Email): String {
        return source.value
    }
}

class StringToEmailConverter : Converter<String, Email> {
    override fun convert(source: String): Email {
        return Email(source)
    }
}

@RestController
class UserController(
    private val userService: UserService,
) {
    data class UserResponse(
        val userId: Int,
        val username: String,
        val email: String,
    ) {
        companion object {
            fun of(user: User): UserResponse {
                return UserResponse(user.userId, user.username, user.email.value)
            }
        }
    }

    @GetMapping("/users/{userId}")
    fun getUser(@PathVariable userId: Int): UserResponse {
        val user = userService.getUser(userId)
        return UserResponse.of(user)
    }

    @GetMapping("/users", params = ["usernames"])
    fun getUsersByUsernames(@RequestParam usernames: List<String>): List<UserResponse> {
        return userService.getUsers(usernames).map { UserResponse.of(it) }
    }

    @GetMapping("/users")
    fun getUsers(): List<UserResponse> {
        return userService.getUsers().map { UserResponse.of(it) }
    }

    @PostMapping("/users")
    fun addUser(@RequestBody req: AddUserRequest): AddUserResponse {
        val newUserId = userService.addUser(req.username, Email(req.email))
        return AddUserResponse(newUserId)
    }

    data class AddUserRequest(
        val username: String,
        val email: String,
    )

    data class AddUserResponse(val userId: Int)

    @PutMapping("/users/{userId}/email")
    fun updateUserEmail(
        @PathVariable userId: Int,
        @RequestBody req: UpdateEmailRequest,
    ): OkResponse {
        userService.updateUserEmail(userId, Email(req.value))
        return OkResponse
    }

    data class UpdateEmailRequest(
        val value: String,
    )

    object OkResponse {
        @Suppress("unused")
        val result = "OK"
    }

    @GetMapping("/users/{userId}/orders")
    fun getUserOrders(@PathVariable userId: Int): List<UserOrderResponse> {
        return userService.getUserOrders(userId)
            .map { UserOrderResponse.of(it) }
    }

    data class UserOrderResponse(
        val username: String,
        val orderId: Int,
        val orderDate: LocalDate,
        val amount: BigDecimal,
    ) {
        companion object {
            fun of(userOrder: UserOrder): UserOrderResponse {
                return UserOrderResponse(
                    username = userOrder.username,
                    orderId = userOrder.orderId,
                    orderDate = userOrder.orderDate,
                    amount = userOrder.amount,
                )
            }
        }
    }
}

@Service
class UserService(private val userRepository: UserRepository) {
    fun getUser(userId: Int): User {
        return userRepository.selectByUserId(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
    }

    fun getUsers(usernames: List<String>? = null): List<User> {
        return if (usernames == null) {
            userRepository.selectAll()
        } else {
            userRepository.selectByUsernames(usernames)
        }
    }

    fun addUser(
        username: String,
        email: Email,
    ): Int {
        return userRepository.insert(username, email)
    }

    fun updateUserEmail(
        userId: Int,
        email: Email,
    ): Long {
        return userRepository.updateEmail(userId, email)
    }

    fun getUserOrders(userId: Int): List<UserOrder> {
        return userRepository.selectOrderByUserId(userId)
    }
}

@Repository
class UserRepository(
    private val client: KueryBlockingClient,
    private val transaction: TransactionTemplate,
) {
    fun selectByUserId(userId: Int): User? {
        return client
            .sql {
                +"SELECT * FROM users WHERE user_id = ${bind(userId)}"
            }
            .singleOrNull()
    }

    fun selectByUsernames(usernames: List<String>): List<User> {
        if (usernames.isEmpty()) {
            return emptyList()
        }
        return client
            .sql {
                +"SELECT * FROM users WHERE username IN (${bind(usernames)})"
            }
            .list()
    }

    fun selectAll(): List<User> {
        return client
            .sql {
                +"SELECT * FROM users"
            }
            .list()
    }

    // Apply transactions using AOP
    @Transactional
    fun insert(
        username: String,
        email: Email,
    ): Int {
        return client
            .sql {
                +"INSERT INTO users (username, email) VALUES (${bind(username)}, ${bind(email)})"
            }
            .generatedValues("user_id")
            .let { (it["GENERATED_KEY"] as BigInteger).toInt() }
            .also {
                // for checking rollback behavior
                throwsExceptionsProbabilistically()
            }
    }

    fun updateEmail(
        userId: Int,
        email: Email,
    ): Long {
        // Programmatically apply transactions
        return transaction.execute {
            client
                .sql {
                    +"UPDATE users SET email = ${bind(email)} WHERE user_id = ${bind(userId)}"
                }
                .rowsUpdated()
                .also {
                    // for checking rollback behavior
                    throwsExceptionsProbabilistically()
                }
        }!!
    }

    fun selectOrderByUserId(userId: Int): List<UserOrder> {
        return client
            .sql {
                +"""
                SELECT users.username, orders.order_id, orders.order_date, orders.amount
                FROM users
                JOIN orders ON users.user_id = orders.user_id
                WHERE
                users.user_id = ${bind(userId)}
                """.trimIndent()
            }
            .list()
    }
}

private fun throwsExceptionsProbabilistically() {
    if (Random.nextInt(2) == 0) {
        error("failed")
    }
}
