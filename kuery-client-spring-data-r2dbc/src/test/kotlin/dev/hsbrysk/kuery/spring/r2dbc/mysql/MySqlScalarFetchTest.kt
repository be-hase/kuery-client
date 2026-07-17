package dev.hsbrysk.kuery.spring.r2dbc.mysql

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import dev.hsbrysk.kuery.core.single
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.awaitRowsUpdated

/**
 * Common scalar-fetch idioms against MySQL: `SELECT EXISTS(...)` (which MySQL returns as
 * BIGINT 0/1) into Boolean, and `COUNT(*)` (BIGINT) into Int/Long.
 */
class MySqlScalarFetchTest {
    private val kueryClient = mysql.kueryClient()

    @BeforeEach
    fun setUp() = runTest {
        mysql.databaseClient.sql("CREATE TABLE scalar_users (user_id INT PRIMARY KEY)").fetch().awaitRowsUpdated()
        mysql.databaseClient.sql("INSERT INTO scalar_users (user_id) VALUES (1), (2)").fetch().awaitRowsUpdated()
    }

    @AfterEach
    fun tearDown() = runTest {
        mysql.databaseClient.sql("DROP TABLE scalar_users").fetch().awaitRowsUpdated()
    }

    @Test
    fun `EXISTS does not map to Boolean`() = runTest {
        // Unlike JDBC, the R2DBC path cannot narrow MySQL's BIGINT 0/1 to Boolean (see also the
        // commented-out Boolean cases in MySqlSingleBasicTypeTest). Fetch it as Long instead.
        assertFailure {
            kueryClient
                .sql { +"SELECT EXISTS(SELECT 1 FROM scalar_users WHERE user_id = 1)" }
                .single<Boolean>()
        }.isInstanceOf(IllegalArgumentException::class)
    }

    @Test
    fun `EXISTS maps to Long`() = runTest {
        val exists: Long = kueryClient
            .sql { +"SELECT EXISTS(SELECT 1 FROM scalar_users WHERE user_id = 1)" }
            .single()
        assertThat(exists).isEqualTo(1L)
    }

    @Test
    fun `COUNT maps to Long`() = runTest {
        val count: Long = kueryClient
            .sql { +"SELECT COUNT(*) FROM scalar_users" }
            .single()
        assertThat(count).isEqualTo(2L)
    }

    @Test
    fun `COUNT narrows to Int`() = runTest {
        val count: Int = kueryClient
            .sql { +"SELECT COUNT(*) FROM scalar_users" }
            .single()
        assertThat(count).isEqualTo(2)
    }

    companion object {
        private val mysql = MySqlTestContainer
    }
}
