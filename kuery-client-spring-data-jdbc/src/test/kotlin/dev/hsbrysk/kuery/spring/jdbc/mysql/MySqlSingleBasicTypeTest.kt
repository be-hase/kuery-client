package dev.hsbrysk.kuery.spring.jdbc.mysql

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.hsbrysk.kuery.core.DelicateKueryClientApi
import dev.hsbrysk.kuery.spring.jdbc.jdbcExceptionProfile
import dev.hsbrysk.kuery.spring.testing.contract.mysql.MySqlSingleBasicTypeContract
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.reflect.KClass

class MySqlSingleBasicTypeTest : MySqlSingleBasicTypeContract() {
    override val database get() = mysql

    override val exceptionProfile get() = jdbcExceptionProfile

    private val blockingClient = MySqlTestContainer.kueryClient()

    // jdbc-only: unlike JDBC's typed retrieval, the r2dbc module cannot map MySQL's BIGINT
    // `SELECT 1` / `SELECT 0` to Boolean.
    @OptIn(DelicateKueryClientApi::class)
    @ParameterizedTest
    @MethodSource("booleanValues")
    fun `single maps a single-column result to Boolean`(
        query: String,
        expected: Any,
        type: KClass<*>,
    ) {
        // when
        val result = blockingClient.sql {
            addUnsafe(query)
        }.single(type)

        // then
        assertThat(result).isEqualTo(expected)
    }

    companion object {
        private val mysql = JdbcMySqlContractDatabase()

        @JvmStatic
        fun booleanValues(): List<Any> = listOf(
            Arguments.of("SELECT 1", true, Boolean::class),
            Arguments.of("SELECT 0", false, Boolean::class),
        )
    }
}
