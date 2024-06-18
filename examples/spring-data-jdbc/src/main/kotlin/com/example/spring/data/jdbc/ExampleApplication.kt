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

fun main(args: Array<String>) {
    runApplication<ExampleApplication>(*args)
}

@SpringBootApplication
class ExampleApplication

@Configuration(proxyBeanMethods = false)
class ExampleConfiguration {
    @Bean
    fun kueryBlockingClient(
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

@Repository
class UserRepository(private val client: KueryBlockingClient) {
    fun selectByUserId(userId: Int): User? {
        return client
            .sql {
                +"SELECT * FROM users WHERE user_id = ${bind(userId)}"
            }
            .singleOrNull()
    }

    fun selectByUsernames(usernames: List<String>): List<User> {
        require(usernames.isNotEmpty())
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
    }

    fun updateEmail(
        userId: Int,
        email: Email,
    ): Long {
        return client
            .sql {
                +"UPDATE users SET email = ${bind(email)} WHERE user_id = ${bind(userId)}"
            }
            .rowsUpdated()
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

@RestController
class ExampleController(
    private val userRepository: UserRepository,
) {
    @GetMapping("/users/{userId}")
    fun getUser(@PathVariable userId: Int): User {
        return userRepository.selectByUserId(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
    }

    @GetMapping("/users", params = ["usernames"])
    fun getUsersByUsernames(@RequestParam usernames: List<String>): List<User> {
        return userRepository.selectByUsernames(usernames)
    }

    @GetMapping("/users")
    fun getUsers(): List<User> {
        return userRepository.selectAll()
    }

    @PostMapping("/users")
    fun addUser(@RequestBody req: AddUserRequest): Int {
        return userRepository.insert(req.username, Email(req.email))
    }

    data class AddUserRequest(
        val username: String,
        val email: String,
    )

    @PutMapping("/users/{userId}/email")
    fun updateUserEmail(
        @PathVariable userId: Int,
        @RequestBody req: UpdateEmailRequest,
    ): Long {
        return userRepository.updateEmail(userId, Email(req.value))
    }

    data class UpdateEmailRequest(
        val value: String,
    )

    @GetMapping("/users/{userId}/orders")
    fun getUserOrders(@PathVariable userId: Int): List<UserOrder> {
        return userRepository.selectOrderByUserId(userId)
    }
}
