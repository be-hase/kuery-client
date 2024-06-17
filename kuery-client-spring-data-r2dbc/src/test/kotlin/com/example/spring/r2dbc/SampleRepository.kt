package com.example.spring.r2dbc

import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.core.list
import dev.hsbrysk.kuery.core.single
import dev.hsbrysk.kuery.core.singleOrNull

data class User(val userId: Int, val username: String, val email: String)

class UserRepository(private val kueryClient: KueryClient) {
    suspend fun singleMap(id: Int): Map<String, Any?> {
        return kueryClient.sql { +"SELECT * FROM users WHERE user_id = ${bind(id)}" }.singleMap()
    }

    suspend fun singleMapOrNull(id: Int): Map<String, Any?>? {
        return kueryClient.sql { +"SELECT * FROM users WHERE user_id = ${bind(id)}" }.singleMapOrNull()
    }

    suspend fun single(id: Int): User {
        return kueryClient.sql { +"SELECT * FROM users WHERE user_id = ${bind(id)}" }.single()
    }

    suspend fun singleOrNull(id: Int): User? {
        return kueryClient.sql { +"SELECT * FROM users WHERE user_id = ${bind(id)}" }.singleOrNull()
    }

    suspend fun listMap(): List<Map<String, Any?>> {
        return kueryClient.sql { +"SELECT * FROM users" }.listMap()
    }

    suspend fun list(): List<User> {
        return kueryClient.sql { +"SELECT * FROM users" }.list()
    }

    suspend fun rowUpdated(
        username: String,
        email: String,
    ): Long {
        return kueryClient
            .sql {
                +"INSERT INTO users (username, email) VALUES (${bind(username)}, ${bind(email)})"
            }
            .rowsUpdated()
    }

    suspend fun generatedValues(
        username: String,
        email: String,
    ): Map<String, Any> {
        return kueryClient
            .sql {
                +"INSERT INTO users (username, email) VALUES (${bind(username)}, ${bind(email)})"
            }
            .generatedValues("user_id")
    }
}
