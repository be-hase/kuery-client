package dev.hsbrysk.kuery.spring.testing.contract.mysql

import assertk.assertFailure
import assertk.assertions.isInstanceOf
import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.spring.testing.ContractDatabase
import dev.hsbrysk.kuery.spring.testing.ExceptionProfile
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Contract shared by the jdbc and r2dbc modules. Each module runs it via a concrete subclass
 * that supplies its `ContractDatabase`.
 *
 * An empty collection is expanded to `IN ()` by Spring's named parameter handling, which MySQL
 * rejects as a syntax error (unlike H2, which accepts it and returns no rows). This pins the
 * behavior; callers must guard against empty collections themselves.
 */
abstract class MySqlEmptyCollectionContract {
    protected abstract val database: ContractDatabase

    protected abstract val exceptionProfile: ExceptionProfile

    // lazy: `database` is not resolvable yet while this base class initializes.
    protected val kueryClient: KueryClient by lazy { database.kueryClient() }

    @Test
    fun `empty collection in IN clause is rejected`() = runTest {
        // Both modules throw an exception named BadSqlGrammarException, but from different
        // packages; the ExceptionProfile absorbs the difference.
        assertFailure {
            kueryClient.sql {
                val emptyIds = emptyList<Long>()
                +"SELECT 1 FROM DUAL WHERE 1 IN ($emptyIds)"
            }.listMap()
        }.isInstanceOf(exceptionProfile.badSqlGrammarException)
    }
}
