package dev.hsbrysk.kuery.spring.r2dbc

import dev.hsbrysk.kuery.spring.testing.ExceptionProfile
import org.springframework.dao.DataRetrievalFailureException
import org.springframework.r2dbc.BadSqlGrammarException

/**
 * The jdbc module reports the same column mismatch as BadSqlGrammarException instead; see
 * `jdbcExceptionProfile` in the jdbc module for the full rationale.
 */
val r2dbcExceptionProfile = ExceptionProfile(
    columnMismatchException = DataRetrievalFailureException::class,
    badSqlGrammarException = BadSqlGrammarException::class,
)
