package dev.hsbrysk.kuery.spring.jdbc.mysql

import dev.hsbrysk.kuery.spring.testing.contract.mysql.MySqlByteArrayBindingContract

class MySqlByteArrayBindingTest : MySqlByteArrayBindingContract() {
    override val database get() = mysql

    companion object {
        private val mysql = JdbcMySqlContractDatabase()
    }
}
