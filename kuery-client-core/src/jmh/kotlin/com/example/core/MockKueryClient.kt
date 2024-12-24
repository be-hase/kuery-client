package com.example.core

import dev.hsbrysk.kuery.core.Sql
import dev.hsbrysk.kuery.core.SqlBuilder
import dev.hsbrysk.kuery.core.internal.SqlIds.id

class MockKueryClient {
    fun sql(
        sqlId: String,
        block: SqlBuilder.() -> Unit,
    ): Pair<String, Sql> {
        val sql = Sql(block)
        return sqlId to sql
    }

    fun sql(block: SqlBuilder.() -> Unit): Pair<String, Sql> {
        val sqlId = block.id()
        return sql(sqlId, block)
    }
}
