package com.example.compiler

import dev.hsbrysk.kuery.core.SqlBuilder2
import dev.hsbrysk.kuery.core.sql2

fun main() {
    val userId = 1
    var status = "active"

    sql2 {
        +"SELECT * FROM users WHERE user_id = $userId AND status = $status"
    }.also {
        println(it)
    }

    sql2 {
        add("SELECT * FROM users WHERE user_id = $userId AND status = $status")
    }.also {
        println(it)
    }

    sql2 {
        +"SELECT *"
        +"FROM users"
        +"WHERE user_id = $userId"
        statusEqualsTo(status)
    }.also {
        println(it)
    }
}

fun SqlBuilder2.statusEqualsTo(status: String) {
    if (status == "active") {
        +"AND status = $status"
    }
}
