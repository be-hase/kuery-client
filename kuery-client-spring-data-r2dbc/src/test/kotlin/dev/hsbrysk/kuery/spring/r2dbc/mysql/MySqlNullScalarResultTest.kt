package dev.hsbrysk.kuery.spring.r2dbc.mysql

import dev.hsbrysk.kuery.spring.testing.contract.mysql.MySqlNullScalarResultContract

class MySqlNullScalarResultTest : MySqlNullScalarResultContract() {
    override val database get() = mysql

    companion object {
        private val mysql = R2dbcMySqlContractDatabase()
    }
}
