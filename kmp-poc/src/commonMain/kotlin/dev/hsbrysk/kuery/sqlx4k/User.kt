package dev.hsbrysk.kuery.sqlx4k

import io.github.smyrgeorge.sqlx4k.annotation.Id
import io.github.smyrgeorge.sqlx4k.annotation.Table

/**
 * PoC entity: sqlx4k-codegen (KSP) generates `generated.UserAutoRowMapper` from this class,
 * which is the reflection-free replacement for the Spring backends' data-class mapping.
 */
@Table("users")
data class User(
    @Id(insert = true)
    val id: Int,
    val name: String,
    val email: String?,
)
