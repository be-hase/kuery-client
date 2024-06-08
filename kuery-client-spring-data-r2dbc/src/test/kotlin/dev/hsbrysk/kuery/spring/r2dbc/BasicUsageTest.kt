package dev.hsbrysk.kuery.spring.r2dbc

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import dev.hsbrysk.kuery.core.SqlDsl
import dev.hsbrysk.kuery.core.flow
import dev.hsbrysk.kuery.core.list
import dev.hsbrysk.kuery.core.single
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DataRetrievalFailureException
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.dao.IncorrectResultSizeDataAccessException
import org.springframework.r2dbc.core.awaitRowsUpdated
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.LocalDate

@Testcontainers
open class BasicUsageTest : MysqlTestContainersBase() {
    @BeforeEach
    fun setUp() = runTest {
        databaseClient.sql(
            """
            CREATE TABLE users (
                user_id INT AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(50) NOT NULL,
                email VARCHAR(100) NOT NULL
            );

            CREATE TABLE orders (
                order_id INT AUTO_INCREMENT PRIMARY KEY,
                user_id INT,
                order_date DATE,
                amount DECIMAL(10, 2),
                FOREIGN KEY (user_id) REFERENCES users(user_id)
            );

            CREATE TABLE products (
                product_id INT AUTO_INCREMENT PRIMARY KEY,
                product_name VARCHAR(100),
                price DECIMAL(10, 2)
            );

            CREATE TABLE order_items (
                order_item_id INT AUTO_INCREMENT PRIMARY KEY,
                order_id INT,
                product_id INT,
                quantity INT,
                FOREIGN KEY (order_id) REFERENCES orders(order_id),
                FOREIGN KEY (product_id) REFERENCES products(product_id)
            );

            INSERT INTO users (username, email) VALUES
            ('user1', 'user1@example.com'),
            ('user2', 'user2@example.com');

            INSERT INTO orders (user_id, order_date, amount) VALUES
            (1, '2023-06-01', 100.00),
            (2, '2023-06-02', 150.00);

            INSERT INTO products (product_name, price) VALUES
            ('Product A', 25.00),
            ('Product B', 50.00);

            INSERT INTO order_items (order_id, product_id, quantity) VALUES
            (1, 1, 2),
            (1, 2, 1),
            (2, 1, 1);
            """.trimIndent(),
        ).fetch().awaitRowsUpdated()
    }

    @AfterEach
    fun testDown() = runTest {
        databaseClient.sql(
            """
            DROP TABLE order_items;
            DROP TABLE orders;
            DROP TABLE products;
            DROP TABLE users;
            """.trimIndent(),
        ).fetch().awaitRowsUpdated()
    }

    data class User(val userId: Int, val username: String, val email: String)

    @Test
    fun singleMap() = runTest {
        val result = kueryClient.sql { +"SELECT * FROM users WHERE user_id = ${bind(1)}" }.singleMap()
        assertThat(result).isEqualTo(
            mapOf(
                "user_id" to 1,
                "username" to "user1",
                "email" to "user1@example.com",
            ),
        )
    }

    @Test
    fun `singleMap no record`() = runTest {
        assertFailure {
            kueryClient.sql { +"SELECT * FROM users WHERE user_id = ${bind(5)}" }.singleMap()
        }.isInstanceOf(EmptyResultDataAccessException::class)
    }

    @Test
    fun `singleMap more than 1 record`() = runTest {
        assertFailure {
            kueryClient.sql { +"SELECT * FROM users" }.singleMap()
        }.isInstanceOf(IncorrectResultSizeDataAccessException::class)
    }

    @Test
    fun singleMapOrNull() = runTest {
        val result = kueryClient.sql { +"SELECT * FROM users WHERE user_id = ${bind(1)}" }.singleMapOrNull()
        assertThat(result).isEqualTo(
            mapOf(
                "user_id" to 1,
                "username" to "user1",
                "email" to "user1@example.com",
            ),
        )
    }

    @Test
    fun `singleMapOrNull no record`() = runTest {
        val result = kueryClient.sql { +"SELECT * FROM users WHERE user_id = ${bind(5)}" }.singleMapOrNull()
        assertThat(result).isNull()
    }

    @Test
    fun `singleMapOrNull more than 1 record`() = runTest {
        assertFailure {
            kueryClient.sql { +"SELECT * FROM users" }.singleMap()
        }.isInstanceOf(IncorrectResultSizeDataAccessException::class)
    }

    @Test
    fun single() = runTest {
        val result: User = kueryClient.sql { +"SELECT * FROM users WHERE user_id = ${bind(1)}" }.single()
        assertThat(result).isEqualTo(
            User(
                userId = 1,
                username = "user1",
                email = "user1@example.com",
            ),
        )
    }

    @Test
    fun `single invalid type`() = runTest {
        data class InvalidUser(val userId: Int, val invalid: String)

        assertFailure {
            kueryClient.sql { +"SELECT * FROM users WHERE user_id = ${bind(1)}" }.single<InvalidUser>()
        }.isInstanceOf(DataRetrievalFailureException::class)
    }

    @Test
    fun `single no record`() = runTest {
        assertFailure {
            kueryClient.sql { +"SELECT * FROM users WHERE user_id = ${bind(5)}" }.single<User>()
        }.isInstanceOf(EmptyResultDataAccessException::class)
    }

    @Test
    fun `single more than 1 record`() = runTest {
        assertFailure {
            kueryClient.sql { +"SELECT * FROM users" }.single<User>()
        }.isInstanceOf(IncorrectResultSizeDataAccessException::class)
    }

    @Test
    fun listMap() = runTest {
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
    fun `listMap empty`() = runTest {
        val result = kueryClient.sql { +"SELECT * FROM users WHERE user_id > ${bind(3)}" }.listMap()
        assertThat(result).isEmpty()
    }

    @Test
    fun list() = runTest {
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
    fun `list empty`() = runTest {
        val result: List<User> = kueryClient.sql { +"SELECT * FROM users WHERE user_id > ${bind(3)}" }.list()
        assertThat(result).isEmpty()
    }

    @Test
    fun flowMap() = runTest {
        val result = kueryClient.sql { +"SELECT * FROM users" }.flowMap().toList()
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
    fun `flowMap empty`() = runTest {
        val result = kueryClient.sql { +"SELECT * FROM users WHERE user_id > ${bind(3)}" }.flowMap().toList()
        assertThat(result).isEmpty()
    }

    @Test
    fun flow() = runTest {
        val result = kueryClient.sql { +"SELECT * FROM users" }.flow<User>().toList()
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
    fun `flow empty`() = runTest {
        val result = kueryClient.sql { +"SELECT * FROM users WHERE user_id > ${bind(3)}" }.flow<User>().toList()
        assertThat(result).isEmpty()
    }

    @Test
    fun rowUpdated() = runTest {
        val result = kueryClient
            .sql {
                +"INSERT INTO users (username, email) VALUES (${bind("user3")}, ${bind("user3@example.com")})"
            }
            .rowsUpdated()
        assertThat(result).isEqualTo(1)
    }

    @Test
    fun `rowUpdated 0`() = runTest {
        val result = kueryClient
            .sql {
                +"DELETE FROM users WHERE user_id = ${bind(5)}"
            }
            .rowsUpdated()
        assertThat(result).isEqualTo(0)
    }

    @Test
    fun `rowUpdated multi`() = runTest {
        val result = kueryClient
            .sql {
                +"UPDATE users SET username = 'updated'"
            }
            .rowsUpdated()
        assertThat(result).isEqualTo(2)
    }

    @Test
    fun generatedValues() = runTest {
        val result = kueryClient
            .sql {
                +"INSERT INTO users (username, email) VALUES (${bind("user3")}, ${bind("user3@example.com")})"
            }
            .generatedValues("user_id")
        assertThat(result).isEqualTo(mapOf("user_id" to 3L))
    }

    @Test
    fun userOrder() = runTest {
        data class UserOrder(
            val username: String,
            val orderId: Int,
            val orderDate: LocalDate,
            val amount: BigDecimal,
        )

        val result: List<UserOrder> = kueryClient
            .sql {
                +"""
                SELECT users.username, orders.order_id, orders.order_date, orders.amount
                FROM users
                JOIN orders ON users.user_id = orders.user_id
                WHERE
                    users.user_id = ${bind(1)}
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
    fun orderProduct() = runTest {
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
    fun `using extension function`() = runTest {
        fun SqlDsl.userIdEqualsTo(userId: Int) {
            +"users.user_id = ${bind(userId)}"
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
}
