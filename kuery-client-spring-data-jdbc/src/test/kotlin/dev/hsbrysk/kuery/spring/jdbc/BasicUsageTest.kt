package dev.hsbrysk.kuery.spring.jdbc

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import dev.hsbrysk.kuery.core.SqlBuilder
import dev.hsbrysk.kuery.core.list
import dev.hsbrysk.kuery.core.single
import dev.hsbrysk.kuery.core.singleOrNull
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.dao.IncorrectResultSizeDataAccessException
import org.springframework.jdbc.UncategorizedSQLException
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate

class BasicUsageTest {
    private val kueryClient = mysql.kueryClient()

    @Suppress("LongMethod")
    @BeforeEach
    fun beforeEach() {
        val queries = listOf(
            """
            CREATE TABLE users (
                user_id INT AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(50) NOT NULL,
                email VARCHAR(100) NOT NULL
            )
            """.trimIndent(),
            """
            CREATE TABLE orders (
                order_id INT AUTO_INCREMENT PRIMARY KEY,
                user_id INT,
                order_date DATE,
                amount DECIMAL(10, 2),
                FOREIGN KEY (user_id) REFERENCES users(user_id)
            )
            """.trimIndent(),
            """
            CREATE TABLE products (
                product_id INT AUTO_INCREMENT PRIMARY KEY,
                product_name VARCHAR(100),
                price DECIMAL(10, 2)
            )
            """.trimIndent(),
            """
            CREATE TABLE order_items (
                order_item_id INT AUTO_INCREMENT PRIMARY KEY,
                order_id INT,
                product_id INT,
                quantity INT,
                FOREIGN KEY (order_id) REFERENCES orders(order_id),
                FOREIGN KEY (product_id) REFERENCES products(product_id)
            )
            """.trimIndent(),
            """
            INSERT INTO users (username, email) VALUES
            ('user1', 'user1@example.com'),
            ('user2', 'user2@example.com')
            """.trimIndent(),
            """
            INSERT INTO orders (user_id, order_date, amount) VALUES
            (1, '2023-06-01', 100.00),
            (2, '2023-06-02', 150.00)
            """.trimIndent(),
            """
            INSERT INTO products (product_name, price) VALUES
            ('Product A', 25.00),
            ('Product B', 50.00);
            """.trimIndent(),
            """
            INSERT INTO order_items (order_id, product_id, quantity) VALUES
            (1, 1, 2),
            (1, 2, 1),
            (2, 1, 1)
            """.trimIndent(),
        )
        queries.forEach {
            mysql.jdbcClient.sql(it).update()
        }
    }

    @AfterEach
    fun afterEach() {
        val queries = listOf(
            "DROP TABLE order_items",
            "DROP TABLE orders",
            "DROP TABLE products",
            "DROP TABLE users",
        )
        queries.forEach {
            mysql.jdbcClient.sql(it).update()
        }
    }

    data class User(
        val userId: Int,
        val username: String,
        val email: String,
    )

    @Test
    fun singleMap() {
        val userId = 1
        val result = kueryClient.sql { +"SELECT * FROM users WHERE user_id = $userId" }.singleMap()
        assertThat(result).isEqualTo(
            mapOf(
                "user_id" to 1,
                "username" to "user1",
                "email" to "user1@example.com",
            ),
        )
    }

    @Test
    fun `singleMap no record`() {
        val userId = 5
        assertFailure {
            kueryClient.sql { +"SELECT * FROM users WHERE user_id = $userId" }.singleMap()
        }.isInstanceOf(EmptyResultDataAccessException::class)
    }

    @Test
    fun `singleMap more than 1 record`() {
        assertFailure {
            kueryClient.sql { +"SELECT * FROM users" }.singleMap()
        }.isInstanceOf(IncorrectResultSizeDataAccessException::class)
    }

    @Test
    fun singleMapOrNull() {
        val userId = 1
        val result = kueryClient.sql { +"SELECT * FROM users WHERE user_id = $userId" }.singleMapOrNull()
        assertThat(result).isEqualTo(
            mapOf(
                "user_id" to 1,
                "username" to "user1",
                "email" to "user1@example.com",
            ),
        )
    }

    @Test
    fun `singleMapOrNull no record`() {
        val userId = 5
        val result = kueryClient.sql { +"SELECT * FROM users WHERE user_id = $userId" }.singleMapOrNull()
        assertThat(result).isNull()
    }

    @Test
    fun `singleMapOrNull more than 1 record`() {
        assertFailure {
            kueryClient.sql { +"SELECT * FROM users" }.singleMap()
        }.isInstanceOf(IncorrectResultSizeDataAccessException::class)
    }

    @Test
    fun single() {
        val userId = 1
        val result: User = kueryClient.sql { +"SELECT * FROM users WHERE user_id = $userId" }.single()
        assertThat(result).isEqualTo(
            User(
                userId = 1,
                username = "user1",
                email = "user1@example.com",
            ),
        )
    }

    @Test
    fun `single invalid type`() {
        data class InvalidUser(
            val userId: Int,
            val invalid: String,
        )

        val userId = 1
        assertFailure {
            kueryClient.sql { +"SELECT * FROM users WHERE user_id = $userId" }.single<InvalidUser>()
        }.isInstanceOf(UncategorizedSQLException::class)
    }

    @Test
    fun `single no record`() {
        val userId = 5
        assertFailure {
            kueryClient.sql { +"SELECT * FROM users WHERE user_id = $userId" }.single<User>()
        }.isInstanceOf(EmptyResultDataAccessException::class)
    }

    @Test
    fun `single more than 1 record`() {
        assertFailure {
            kueryClient.sql { +"SELECT * FROM users" }.single<User>()
        }.isInstanceOf(IncorrectResultSizeDataAccessException::class)
    }

    @Test
    fun singleOrNull() {
        val userId = 1
        val result: User? = kueryClient.sql { +"SELECT * FROM users WHERE user_id = $userId" }.singleOrNull()
        assertThat(result).isEqualTo(
            User(
                userId = 1,
                username = "user1",
                email = "user1@example.com",
            ),
        )
    }

    @Test
    fun `singleOrNull no record`() {
        val userId = 5
        val result: User? = kueryClient.sql { +"SELECT * FROM users WHERE user_id = $userId" }.singleOrNull()
        assertThat(result).isNull()
    }

    @Test
    fun `singleOrNull more than 1 record`() {
        assertFailure {
            kueryClient.sql { +"SELECT * FROM users" }.singleOrNull<User>()
        }.isInstanceOf(IncorrectResultSizeDataAccessException::class)
    }

    @Test
    fun listMap() {
        val result = kueryClient.sql { +"SELECT * FROM users" }.listMap()
        assertThat(result).isEqualTo(
            listOf(
                mapOf(
                    "user_id" to 1,
                    "username" to "user1",
                    "email" to "user1@example.com",
                ),
                mapOf(
                    "user_id" to 2,
                    "username" to "user2",
                    "email" to "user2@example.com",
                ),
            ),
        )
    }

    @Test
    fun `listMap empty`() {
        val userId = 3
        val result = kueryClient.sql { +"SELECT * FROM users WHERE user_id > $userId" }.listMap()
        assertThat(result).isEmpty()
    }

    @Test
    fun list() {
        val result: List<User> = kueryClient.sql { +"SELECT * FROM users" }.list()
        assertThat(result).isEqualTo(
            listOf(
                User(
                    userId = 1,
                    username = "user1",
                    email = "user1@example.com",
                ),
                User(
                    userId = 2,
                    username = "user2",
                    email = "user2@example.com",
                ),
            ),
        )
    }

    @Test
    fun `list empty`() {
        val userId = 3
        val result: List<User> = kueryClient.sql { +"SELECT * FROM users WHERE user_id > $userId" }.list()
        assertThat(result).isEmpty()
    }

    @Test
    fun rowUpdated() {
        val username = "user3"
        val email = "user3"
        val result = kueryClient
            .sql {
                +"INSERT INTO users (username, email) VALUES ($username, $email)"
            }
            .rowsUpdated()
        assertThat(result).isEqualTo(1)
    }

    @Test
    fun `rowUpdated 0`() {
        val userId = 5
        val result = kueryClient
            .sql {
                +"DELETE FROM users WHERE user_id = $userId"
            }
            .rowsUpdated()
        assertThat(result).isEqualTo(0)
    }

    @Test
    fun `rowUpdated multi`() {
        val result = kueryClient
            .sql {
                +"UPDATE users SET username = 'updated'"
            }
            .rowsUpdated()
        assertThat(result).isEqualTo(2)
    }

    @Test
    fun generatedValues() {
        val username = "user3"
        val email = "user3"
        val result = kueryClient
            .sql {
                +"INSERT INTO users (username, email) VALUES ($username, $email)"
            }
            .generatedValues("user_id")
        // Humm. mysql/jdbc specifications?
        assertThat(result["GENERATED_KEY"]).isEqualTo(BigInteger.valueOf(3))
    }

    @Test
    fun userOrder() {
        data class UserOrder(
            val username: String,
            val orderId: Int,
            val orderDate: LocalDate,
            val amount: BigDecimal,
        )

        val userId = 1
        val result: List<UserOrder> = kueryClient
            .sql {
                +"""
                SELECT users.username, orders.order_id, orders.order_date, orders.amount
                FROM users
                JOIN orders ON users.user_id = orders.user_id
                WHERE
                    users.user_id = $userId
                """.trimIndent()
            }
            .list()
        assertThat(result).isEqualTo(
            listOf(
                UserOrder(
                    username = "user1",
                    orderId = 1,
                    orderDate = LocalDate.of(2023, 6, 1),
                    amount = BigDecimal("100.00"),
                ),
            ),
        )
    }

    @Test
    fun orderProduct() {
        data class OrderProduct(
            val orderId: Int,
            val productName: String,
            val quantity: Int,
        )

        val result: List<OrderProduct> = kueryClient
            .sql {
                +"""
                SELECT orders.order_id, products.product_name, order_items.quantity
                FROM orders
                JOIN order_items ON orders.order_id = order_items.order_id
                JOIN products ON order_items.product_id = products.product_id
                ORDER BY orders.order_id
                """.trimIndent()
            }
            .list()
        assertThat(result).isEqualTo(
            listOf(
                OrderProduct(orderId = 1, productName = "Product A", quantity = 2),
                OrderProduct(orderId = 1, productName = "Product B", quantity = 1),
                OrderProduct(orderId = 2, productName = "Product A", quantity = 1),
            ),
        )
    }

    @Test
    fun `using extension function`() {
        fun SqlBuilder.userIdEqualsTo(userId: Int) {
            addUnsafe("users.user_id = ${bind(userId)}")
        }

        val result: User = kueryClient
            .sql {
                +"SELECT * FROM users"
                +"WHERE"
                userIdEqualsTo(1)
            }
            .single()
        assertThat(result).isEqualTo(User(userId = 1, username = "user1", email = "user1@example.com"))
    }

    companion object {
        private val mysql = MySqlTestContainer()

        @AfterAll
        @JvmStatic
        fun afterAll() {
            mysql.close()
        }
    }
}
