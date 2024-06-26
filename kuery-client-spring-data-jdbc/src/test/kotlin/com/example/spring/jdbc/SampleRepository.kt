package com.example.spring.jdbc

import dev.hsbrysk.kuery.core.KueryBlockingClient
import dev.hsbrysk.kuery.core.list
import dev.hsbrysk.kuery.core.single
import dev.hsbrysk.kuery.core.singleOrNull

data class User(
    val userId: Int,
    val username: String,
    val email: String,
)

class UserRepository(private val kueryClient: KueryBlockingClient) {
    fun singleMap(id: Int): Map<String, Any?> =
        kueryClient.sql { +"SELECT * FROM users WHERE user_id = $id" }.singleMap()

    fun singleMapOrNull(id: Int): Map<String, Any?>? = kueryClient
        .sql {
            +"SELECT * FROM users WHERE user_id = $id"
        }
        .singleMapOrNull()

    fun single(id: Int): User = kueryClient.sql { +"SELECT * FROM users WHERE user_id = $id" }.single()

    fun singleOrNull(id: Int): User? = kueryClient.sql { +"SELECT * FROM users WHERE user_id = $id" }.singleOrNull()

    fun listMap(): List<Map<String, Any?>> = kueryClient.sql { +"SELECT * FROM users" }.listMap()

    fun list(): List<User> = kueryClient.sql { +"SELECT * FROM users" }.list()

    fun rowUpdated(
        username: String,
        email: String,
    ): Long = kueryClient
        .sql {
            +"INSERT INTO users (username, email) VALUES ($username, $email)"
        }
        .rowsUpdated()

    fun generatedValues(
        username: String,
        email: String,
    ): Map<String, Any> = kueryClient
        .sql {
            +"INSERT INTO users (username, email) VALUES ($username, $email)"
        }
        .generatedValues("user_id")
}
