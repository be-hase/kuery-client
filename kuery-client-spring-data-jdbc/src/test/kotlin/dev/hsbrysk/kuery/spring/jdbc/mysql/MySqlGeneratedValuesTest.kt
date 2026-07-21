package dev.hsbrysk.kuery.spring.jdbc.mysql

import assertk.assertFailure
import assertk.assertions.isInstanceOf
import dev.hsbrysk.kuery.spring.testing.contract.mysql.MySqlGeneratedValuesContract
import org.junit.jupiter.api.Test
import org.springframework.dao.EmptyResultDataAccessException
import java.math.BigInteger

/**
 * Pins MySQL Connector/J's generated-keys behavior: the auto-increment value is reported under the
 * driver-specific "GENERATED_KEY" label (as a BigInteger), regardless of the requested column names.
 */
class MySqlGeneratedValuesTest : MySqlGeneratedValuesContract() {
    override val database get() = mysql

    override val expectedGeneratedValues get() = mapOf<String, Any>("GENERATED_KEY" to BigInteger.valueOf(1))

    private val blockingClient = MySqlTestContainer.kueryClient()

    @Test
    fun `generatedValues throws EmptyResultDataAccessException when no keys are generated`() {
        // Unlike H2 (which reports the affected row's identity even for UPDATE), MySQL emits no
        // generated keys here, exercising the EmptyResultDataAccessException failure path.
        assertFailure {
            blockingClient
                .sql { +"UPDATE users SET username = 'updated' WHERE user_id = 1" }
                .generatedValues()
        }.isInstanceOf(EmptyResultDataAccessException::class)
    }

    companion object {
        private val mysql = JdbcMySqlContractDatabase()
    }
}
