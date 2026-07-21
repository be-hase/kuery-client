package dev.hsbrysk.kuery.spring.r2dbc.mysql

import dev.hsbrysk.kuery.spring.r2dbc.r2dbcExceptionProfile
import dev.hsbrysk.kuery.spring.testing.contract.mysql.MySqlSingleBasicTypeContract

class MySqlSingleBasicTypeTest : MySqlSingleBasicTypeContract() {
    override val database get() = mysql

    override val exceptionProfile get() = r2dbcExceptionProfile

    // Unlike JDBC, mapping MySQL's `SELECT 1` / `SELECT 0` to Boolean does not pass here, so the
    // Boolean cases live on the jdbc concrete subclass only.

    companion object {
        private val mysql = R2dbcMySqlContractDatabase()
    }
}
