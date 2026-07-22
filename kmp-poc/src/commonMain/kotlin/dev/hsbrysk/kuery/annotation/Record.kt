package dev.hsbrysk.kuery.annotation

/**
 * PoC: marks a data class as a query-result record. The kuery KSP processor generates a
 * sqlx4k `RowMapper` for it (`<Name>RowMapper`, same package).
 *
 * Unlike sqlx4k's `@Table`, there is no table name (and no `@Id` requirement): kuery-client
 * users write the SQL themselves, so the annotation only drives row mapping. Column names are
 * the snake_case form of the property names.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Record
