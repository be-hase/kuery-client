package com.example.autotrim

import dev.hsbrysk.kuery.core.Sql

/**
 * The same query as `com.example.runtimetrim.RuntimeTrimQueries` in the sibling benchmark
 * module, but this module is compiled with `autoTrimIndent=true`, so the trimming is folded
 * into the fragments at compile time and no trim runs when the query is built.
 *
 * Must stay the same query as RuntimeTrimQueries — QueryFixturesTest asserts the equality.
 */
object AutoTrimQueries {
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
        """
    }
}
