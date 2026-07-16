package dev.hsbrysk.kuery.spring.jdbc.mysql

import assertk.assertFailure
import assertk.assertions.isInstanceOf
import org.junit.jupiter.api.Test
import org.springframework.jdbc.BadSqlGrammarException

/**
 * An empty collection is expanded to `IN ()` by Spring's named parameter handling, which MySQL
 * rejects as a syntax error (unlike H2, which accepts it and returns no rows). This pins the
 * behavior; callers must guard against empty collections themselves.
 */
class MySqlEmptyCollectionTest {
    private val kueryClient = mysql.kueryClient()

    @Test
    fun `empty collection in IN clause is rejected`() {
        assertFailure {
            kueryClient.sql {
                val emptyIds = emptyList<Long>()
                +"SELECT 1 FROM DUAL WHERE 1 IN ($emptyIds)"
            }.listMap()
        }.isInstanceOf(BadSqlGrammarException::class)
    }

    companion object {
        private val mysql = MySqlTestContainer
    }
}
