@file:Suppress("UNREACHABLE_CODE")

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.r2dbc.core.DatabaseClient
import java.util.function.Function

fun main() {
    val r2dbcClient: DatabaseClient = checkNotNull(null)
    r2dbcClient.sql("").filter(Function { it.returnGeneratedValues() })

    val jdbcClient: JdbcClient = checkNotNull(null)
    jdbcClient.sql("").param("", null)
}
