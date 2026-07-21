package dev.hsbrysk.kuery.spring.r2dbc.mysql

import dev.hsbrysk.kuery.spring.testing.contract.mysql.MySqlGeneratedValuesContract

/**
 * Pins r2dbc-mysql's generated-values behavior: the driver synthesizes a single lastInsertId row
 * (a Long) under the requested column name. The zero/multiple-rows failure paths cannot be
 * reproduced with MySQL; those are covered in the postgres package instead.
 */
class MySqlGeneratedValuesTest : MySqlGeneratedValuesContract() {
    override val database get() = mysql

    override val expectedGeneratedValues get() = mapOf<String, Any>("user_id" to 1L)

    companion object {
        private val mysql = R2dbcMySqlContractDatabase()
    }
}
