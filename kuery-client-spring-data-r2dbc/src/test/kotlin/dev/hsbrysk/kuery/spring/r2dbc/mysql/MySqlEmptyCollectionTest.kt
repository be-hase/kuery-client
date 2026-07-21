package dev.hsbrysk.kuery.spring.r2dbc.mysql

import dev.hsbrysk.kuery.spring.r2dbc.r2dbcExceptionProfile
import dev.hsbrysk.kuery.spring.testing.contract.mysql.MySqlEmptyCollectionContract

class MySqlEmptyCollectionTest : MySqlEmptyCollectionContract() {
    override val database get() = mysql

    override val exceptionProfile get() = r2dbcExceptionProfile

    companion object {
        private val mysql = R2dbcMySqlContractDatabase()
    }
}
