package com.example.runtimetrim

import dev.hsbrysk.kuery.core.Sql

/**
 * Queries compiled WITHOUT autoTrimIndent (this module's default), the way users write them
 * today: an explicit `.trimIndent()` that runs at runtime on the placeholder-interpolated
 * string every time the query is built, plus an untrimmed variant as the no-trim floor.
 *
 * Must stay the same query as `com.example.autotrim.AutoTrimQueries` in the sibling
 * benchmark-auto-trim module — QueryFixturesTest asserts the equality.
 */
object RuntimeTrimQueries {
    fun update(
        id: Int,
        name: String,
        age: Int,
        address: String,
    ): Sql = Sql {
        +"""
        UPDATE user
        SET
            name = $name,
            age = $age,
            address = $address
        WHERE
            id = $id
        """.trimIndent()
    }

    fun updateUntrimmed(
        id: Int,
        name: String,
        age: Int,
        address: String,
    ): Sql = Sql {
        +"""
        UPDATE user
        SET
            name = $name,
            age = $age,
            address = $address
        WHERE
            id = $id
        """
    }
}
