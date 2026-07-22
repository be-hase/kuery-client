package dev.hsbrysk.kuery.sqlx4k

import dev.hsbrysk.kuery.annotation.Record

/**
 * PoC entity: the kuery KSP processor (:kmp-poc:codegen) generates `UserRowMapper` from this
 * class — the reflection-free replacement for the Spring backends' data-class mapping.
 * No table name, no id: @Record only drives row mapping.
 */
@Record
data class User(
    val id: Int,
    val name: String,
    val email: String?,
)
