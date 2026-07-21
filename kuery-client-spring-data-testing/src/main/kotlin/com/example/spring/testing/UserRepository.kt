package com.example.spring.testing

import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.core.list
import dev.hsbrysk.kuery.core.single
import dev.hsbrysk.kuery.core.singleOrNull

data class User(
    val userId: Int,
    val username: String,
    val email: String,
)

/**
 * Simulates user code calling the client from outside the library packages, so that the
 * observation/sqlid contracts observe the auto-generated sqlId of a realistic call site. The
 * compiler plugin rewrites the literal blocks here, so both modules see the same sqlId
 * (`com.example.spring.testing.UserRepository.<method>`) even when jdbc runs the contract
 * through the blocking adapter.
 */
class UserRepository(private val kueryClient: KueryClient) {
    suspend fun singleMap(id: Int): Map<String, Any?> =
        kueryClient.sql { +"SELECT * FROM users WHERE user_id = $id" }.singleMap()

    suspend fun singleMapOrNull(id: Int): Map<String, Any?>? = kueryClient
        .sql {
            +"SELECT * FROM users WHERE user_id = $id"
        }
        .singleMapOrNull()

    suspend fun single(id: Int): User = kueryClient.sql { +"SELECT * FROM users WHERE user_id = $id" }.single()

    suspend fun singleOrNull(id: Int): User? = kueryClient
        .sql {
            +"SELECT * FROM users WHERE user_id = $id"
        }
        .singleOrNull()

    suspend fun listMap(): List<Map<String, Any?>> = kueryClient.sql { +"SELECT * FROM users" }.listMap()

    suspend fun list(): List<User> = kueryClient.sql { +"SELECT * FROM users" }.list()

    suspend fun rowUpdated(
        username: String,
        email: String,
    ): Long = kueryClient
        .sql {
            +"INSERT INTO users (username, email) VALUES ($username, $email)"
        }
        .rowsUpdated()

    suspend fun generatedValues(
        username: String,
        email: String,
    ): Map<String, Any> = kueryClient
        .sql {
            +"INSERT INTO users (username, email) VALUES ($username, $email)"
        }
        .generatedValues("user_id")
}
