package dev.hsbrysk.kuery.spring.testing.contract.mysql

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import dev.hsbrysk.kuery.core.DelicateKueryClientApi
import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.spring.testing.ContractDatabase
import dev.hsbrysk.kuery.spring.testing.ExceptionProfile
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import java.net.URI
import kotlin.reflect.KClass

/**
 * Contract shared by the jdbc and r2dbc modules. Each module runs it via a concrete subclass
 * that supplies its `ContractDatabase`. Whether `SELECT 1` / `SELECT 0` maps to Boolean is
 * module-specific, so those cases live on the jdbc concrete subclass.
 */
abstract class MySqlSingleBasicTypeContract {
    protected abstract val database: ContractDatabase

    protected abstract val exceptionProfile: ExceptionProfile

    // lazy: `database` is not resolvable yet while this base class initializes.
    protected val kueryClient: KueryClient by lazy {
        database.kueryClient(
            listOf(
                StringToStringWrapperConverter(),
            ),
        )
    }

    data class StringWrapper(val value: String)

    @ReadingConverter
    class StringToStringWrapperConverter : Converter<String, StringWrapper> {
        override fun convert(source: String): StringWrapper = StringWrapper(source)
    }

    @OptIn(DelicateKueryClientApi::class)
    @Test
    fun `single maps a single-column result to each basic type`() = runTest {
        // junit-params is not on this module's classpath, so the cases run as a loop in one test.
        val cases: List<Triple<String, Any, KClass<*>>> = listOf(
            Triple("SELECT 1", 1.toShort(), Short::class),
            Triple("SELECT 1", 1, Int::class),
            Triple("SELECT 1", 1L, Long::class),
            Triple("SELECT '1'", 1.toShort(), Short::class),
            Triple("SELECT '1'", 1, Int::class),
            Triple("SELECT '1'", 1L, Long::class),
            Triple("SELECT 'hoge'", "hoge", String::class),
            Triple("SELECT 1", "1", String::class),
            Triple("SELECT 'https://example.com'", URI("https://example.com"), URI::class),
        )
        cases.forEach { (query, expected, type) ->
            // when
            val result = kueryClient.sql {
                addUnsafe(query)
            }.single(type)

            // then
            assertThat(result, name = "$query AS ${type.simpleName}").isEqualTo(expected)
        }
    }

    @Test
    fun `single with a non-simple data class type is rejected`() = runTest {
        // The concrete exception type is module-specific; each module's ExceptionProfile
        // documents why.
        assertFailure {
            kueryClient.sql {
                +"SELECT 'hoge'"
            }.single(StringWrapper::class)
        }.isInstanceOf(exceptionProfile.columnMismatchException)
    }

    @Test
    fun `list maps each row of a single-column result`() = runTest {
        // when
        val result = kueryClient.sql {
            +"SELECT 1 UNION SELECT 0"
        }.list(Int::class)

        // then
        assertThat(result).isEqualTo(listOf(1, 0))
    }
}
