package dev.hsbrysk.kuery.spring.r2dbc.mysql

import dev.hsbrysk.kuery.spring.testing.contract.mysql.MySqlByteArrayBindingContract

/**
 * The single-binary-value binding is why ByteArray is excluded from the primitive-array boxing in
 * DefaultSpringR2dbcKueryClient.
 */
class MySqlByteArrayBindingTest : MySqlByteArrayBindingContract() {
    override val database get() = mysql

    companion object {
        private val mysql = R2dbcMySqlContractDatabase()
    }
}
