package com.example.core

import dev.hsbrysk.kuery.core.SqlBuilder

/**
 * A block implementation loaded by an isolated class loader in `SqlIdsTest`
 * to verify that `SqlIds` does not pin the class loader.
 */
internal class IsolatedBlock : (SqlBuilder) -> Unit {
    override fun invoke(p1: SqlBuilder) = Unit
}
