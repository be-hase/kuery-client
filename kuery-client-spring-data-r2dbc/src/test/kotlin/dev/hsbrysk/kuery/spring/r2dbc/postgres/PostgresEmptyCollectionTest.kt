package dev.hsbrysk.kuery.spring.r2dbc.postgres

import assertk.assertFailure
import assertk.assertions.isInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.BadSqlGrammarException

/**
 * An empty collection is expanded to `IN ()` by Spring's named parameter handling, which
 * PostgreSQL rejects as a syntax error (unlike H2, which accepts it and returns no rows). This
 * pins the behavior; callers must guard against empty collections themselves.
 */
class PostgresEmptyCollectionTest {
    private val kueryClient = postgres.kueryClient()

    @Test
    fun `empty collection in IN clause is rejected`() = runTest {
        assertFailure {
            kueryClient.sql {
                val emptyIds = emptyList<Long>()
                +"SELECT 1 WHERE 1 IN ($emptyIds)"
            }.listMap()
        }.isInstanceOf(BadSqlGrammarException::class)
    }

    companion object {
        private val postgres = PostgresTestContainer
    }
}
