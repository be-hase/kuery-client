package com.example.runtimetrim

import dev.hsbrysk.kuery.core.Sql

/**
 * Queries compiled WITHOUT autoTrimIndent (this module's default), the way users write them
 * today: an explicit `.trimIndent()` that runs at runtime on the placeholder-interpolated
 * string every time the query is built, plus an untrimmed variant as the no-trim floor.
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
