package dev.hsbrysk.kuery.spring.jdbc.mysql

import assertk.assertFailure
import assertk.assertions.isInstanceOf
import org.junit.jupiter.api.Test
import org.springframework.dao.QueryTimeoutException

/**
 * queryTimeoutSeconds must actually abort a query that exceeds the timeout (the H2
 * StatementOptionsTest only verifies the option does not break fast queries).
 */
class MySqlQueryTimeoutTest {
    private val kueryClient = mysql.kueryClient()

    @Test
    fun `queryTimeoutSeconds aborts a query that runs too long`() {
        assertFailure {
            kueryClient
                .sql { +"SELECT SLEEP(10)" }
                .queryTimeoutSeconds(1)
                .singleMap()
        }.isInstanceOf(QueryTimeoutException::class)
    }

    companion object {
        private val mysql = MySqlTestContainer
    }
}
