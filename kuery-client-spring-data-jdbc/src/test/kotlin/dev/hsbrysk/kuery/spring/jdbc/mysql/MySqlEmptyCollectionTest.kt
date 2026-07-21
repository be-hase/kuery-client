package dev.hsbrysk.kuery.spring.jdbc.mysql

import dev.hsbrysk.kuery.spring.jdbc.jdbcExceptionProfile
import dev.hsbrysk.kuery.spring.testing.contract.mysql.MySqlEmptyCollectionContract

class MySqlEmptyCollectionTest : MySqlEmptyCollectionContract() {
    override val database get() = mysql

    override val exceptionProfile get() = jdbcExceptionProfile

    companion object {
        private val mysql = JdbcMySqlContractDatabase()
    }
}
