package dev.hsbrysk.kuery.spring.testing

import kotlin.reflect.KClass

/**
 * Exception types on which the jdbc and r2dbc implementations legitimately diverge. Contracts
 * assert against these instead of a concrete type, and each module supplies its own profile, so
 * this module never depends on either implementation's exception classes. Exceptions both
 * implementations throw identically (e.g. `EmptyResultDataAccessException`) are asserted
 * directly in the contracts instead.
 */
class ExceptionProfile(
    /**
     * Thrown when a data class constructor parameter has no matching column in the selection,
     * even if the property is nullable (jdbc: `BadSqlGrammarException`, r2dbc:
     * `DataRetrievalFailureException`; each module's profile documents why).
     */
    val columnMismatchException: KClass<out Throwable>,
    /**
     * Thrown when the database rejects a statement as syntactically invalid (e.g. an empty
     * collection interpolated into `IN ()`). Both implementations throw an exception named
     * `BadSqlGrammarException`, but from different packages (`org.springframework.jdbc` vs
     * `org.springframework.r2dbc`), so contracts must go through the profile.
     */
    val badSqlGrammarException: KClass<out Throwable>,
)
