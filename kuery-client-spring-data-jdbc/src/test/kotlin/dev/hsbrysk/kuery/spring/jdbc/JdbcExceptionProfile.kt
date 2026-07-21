package dev.hsbrysk.kuery.spring.jdbc

import dev.hsbrysk.kuery.spring.testing.ExceptionProfile
import org.springframework.jdbc.BadSqlGrammarException

/**
 * [BadSqlGrammarException] looks odd for the column-mismatch mapping problem, but it is
 * consistent across drivers (verified against MySQL too, not just H2): DataClassRowMapper
 * resolves each constructor parameter via ResultSet.findColumn, drivers report an unknown column
 * as SQLState class 42 (42S22, the same class as a typo'd column in SQL text), and Spring's
 * exception translation maps that class to BadSqlGrammarException. The r2dbc module reports the
 * same mismatch as DataRetrievalFailureException instead.
 */
val jdbcExceptionProfile = ExceptionProfile(
    columnMismatchException = BadSqlGrammarException::class,
)
