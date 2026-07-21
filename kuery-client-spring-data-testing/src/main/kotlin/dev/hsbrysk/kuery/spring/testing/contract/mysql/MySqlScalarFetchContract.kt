package dev.hsbrysk.kuery.spring.testing.contract.mysql

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.core.single
import dev.hsbrysk.kuery.spring.testing.ContractDatabase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Contract shared by the jdbc and r2dbc modules. Each module runs it via a concrete subclass
 * that supplies its `ContractDatabase`.
 *
 * Common scalar-fetch idioms against MySQL: `COUNT(*)` (BIGINT) into Int/Long. Whether
 * `SELECT EXISTS(...)` (which MySQL returns as BIGINT 0/1) maps to Boolean is module-specific,
 * so the EXISTS cases live on the concrete subclasses.
 */
abstract class MySqlScalarFetchContract {
    protected abstract val database: ContractDatabase

    // lazy: `database` is not resolvable yet while this base class initializes.
    protected val kueryClient: KueryClient by lazy { database.kueryClient() }

    @BeforeEach
    fun setUpScalarUsersTable() {
        database.execute("CREATE TABLE scalar_users (user_id INT PRIMARY KEY)")
        database.execute("INSERT INTO scalar_users (user_id) VALUES (1), (2)")
    }

    @AfterEach
    fun dropScalarUsersTable() {
        database.execute("DROP TABLE IF EXISTS scalar_users")
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
}
