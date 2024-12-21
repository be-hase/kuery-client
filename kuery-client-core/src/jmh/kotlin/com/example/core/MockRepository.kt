package com.example.core

import dev.hsbrysk.kuery.core.Sql

class MockRepository(private val client: MockKueryClient) {
    fun select(id: Int): Pair<String, Sql> = client.sql("sqlId") {
        addUnsafe("SELECT * FROM users WHERE id = ${bind(id)}")
    }

    fun selectWithAutoIdGeneration(id: Int): Pair<String, Sql> = client.sql {
        addUnsafe("SELECT * FROM users WHERE id = ${bind(id)}")
    }
}
