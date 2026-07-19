package dev.hsbrysk.kuery.spring.jdbc.internal

import dev.hsbrysk.kuery.core.CloseableSequence
import dev.hsbrysk.kuery.core.KueryBlockingClient
import dev.hsbrysk.kuery.core.KueryClientInternalApi
import dev.hsbrysk.kuery.core.NamedSqlParameter
import dev.hsbrysk.kuery.core.Sql
import dev.hsbrysk.kuery.core.SqlBuilder
import dev.hsbrysk.kuery.core.internal.SqlIds.id
import dev.hsbrysk.kuery.core.observation.KueryClientFetchContext
import dev.hsbrysk.kuery.core.observation.KueryClientFetchObservationConvention
import dev.hsbrysk.kuery.core.observation.KueryClientObservationDocumentation
import dev.hsbrysk.kuery.spring.jdbc.SqlIdInjector
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import org.springframework.beans.BeanUtils
import org.springframework.core.KotlinDetector
import org.springframework.core.convert.ConversionService
import org.springframework.dao.support.DataAccessUtils
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions
import org.springframework.jdbc.core.ColumnMapRowMapper
import org.springframework.jdbc.core.DataClassRowMapper
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.SingleColumnRowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.core.simple.JdbcClient.MappedQuerySpec
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec
import org.springframework.jdbc.support.GeneratedKeyHolder
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import java.lang.reflect.Array as ReflectArray

@OptIn(KueryClientInternalApi::class)
internal class DefaultSpringJdbcKueryClient(
    private val jdbcClient: JdbcClient,
    private val conversionService: ConversionService,
    private val customConversions: JdbcCustomConversions,
    private val observationRegistry: ObservationRegistry?,
    private val observationConvention: KueryClientFetchObservationConvention?,
    private val enableAutoSqlIdGeneration: Boolean,
) : KueryBlockingClient {
    private val defaultObservationConvention = KueryClientFetchObservationConvention.default()
    private val rowMapperCache = ConcurrentHashMap<KClass<*>, RowMapper<*>>()

    override fun sql(
        sqlId: String,
        block: SqlBuilder.() -> Unit,
    ): KueryBlockingClient.FetchSpec {
        val sql = Sql(block)
        return FetchSpec(sqlId, sql)
    }

    override fun sql(block: SqlBuilder.() -> Unit): KueryBlockingClient.FetchSpec {
        val sqlId = if (enableAutoSqlIdGeneration) block.id() else "NONE"
        return sql(sqlId, block)
    }

    private fun JdbcClient.sql(sql: Sql): StatementSpec = sql.parameters.fold(this.sql(sql.body)) { acc, parameter ->
        if (parameter.value != null) {
            acc.bind(parameter)
        } else {
            acc.param(parameter.name, null)
        }
    }

    private fun StatementSpec.bind(parameter: NamedSqlParameter): StatementSpec =
        bindValue(parameter.name, checkNotNull(parameter.value))

    private fun StatementSpec.bindValue(
        name: String,
        value: Any,
    ): StatementSpec {
        val targetType = customConversions.getCustomWriteTarget(value::class.java)
        if (targetType.isPresent) {
            // The Converter contract allows a null result; bind it as SQL NULL.
            return param(name, conversionService.convert(value, targetType.get()))
        }

        if (KotlinDetector.isInlineClass(value.javaClass)) {
            // Unwrap and re-dispatch so the underlying value goes through the full conversion
            // pipeline again (custom converters, enums, nested value classes, ...).
            val unwrapped = ValueClasses.unbox(value) ?: return param(name, null)
            return bindValue(name, unwrapped)
        }

        return when (value) {
            is Collection<*> -> param(name, convertCollection(value))
            is Array<*> -> param(name, convertArray(value))
            is Enum<*> -> param(name, value.name)
            else -> param(name, value)
        }
    }

    // NOTE: The conversion helpers below (convertCollection / convertArray / componentWriteTarget /
    // convertElement) are intentionally duplicated in DefaultSpringR2dbcKueryClient; there is no
    // shared Spring-dependent module to host them. Keep both copies in sync.
    private fun convertCollection(collection: Collection<*>): Collection<*> = collection.map { convertElement(it) }

    // The runtime component type must be preserved (e.g. String[] stays String[]); drivers
    // resolve the SQL array type from it, and pgjdbc rejects Object[] outright.
    private fun convertArray(array: Array<*>): Array<*> {
        val converted = array.map { convertElement(it) }
        val targetType = componentWriteTarget(array.javaClass.componentType)
        if (targetType == null && array.indices.all { converted[it] === array[it] }) {
            return array
        }
        val inferredType = converted.mapNotNull { it?.javaClass }.distinct().singleOrNull() ?: Any::class.java
        val componentType = targetType ?: inferredType
        val result = ReflectArray.newInstance(componentType, array.size)
        converted.forEachIndexed { index, value -> ReflectArray.set(result, index, value) }
        return result as Array<*>
    }

    // The component type itself carries the conversion intent even when the elements don't
    // (all-null or empty arrays), e.g. SampleEnum[] must become String[] regardless of contents.
    private fun componentWriteTarget(componentType: Class<*>): Class<*>? {
        val targetType = customConversions.getCustomWriteTarget(componentType)
        return when {
            targetType.isPresent -> targetType.get()
            KotlinDetector.isInlineClass(componentType) -> {
                val underlying = ValueClasses.underlyingType(componentType)
                componentWriteTarget(underlying) ?: underlying
            }
            Enum::class.java.isAssignableFrom(componentType) -> String::class.java
            else -> null
        }
    }

    private fun convertElement(element: Any?): Any? {
        if (element == null) {
            return null
        }
        val targetType = customConversions.getCustomWriteTarget(element::class.java)
        return when {
            targetType.isPresent -> conversionService.convert(element, targetType.get())
            KotlinDetector.isInlineClass(element.javaClass) -> convertElement(ValueClasses.unbox(element))
            // composite IN `(a, b) IN ($pairs)` passes each row as an Object[] entry in a Collection
            element is Array<*> -> convertArray(element)
            element is Enum<*> -> element.name
            else -> element
        }
    }

    @Suppress("TooManyFunctions")
    inner class FetchSpec(
        private val sqlId: String,
        private val sql: Sql,
        private val fetchSizeOpt: Int? = null,
        private val maxRowsOpt: Int? = null,
        private val queryTimeoutSecondsOpt: Int? = null,
    ) : KueryBlockingClient.FetchSpec {
        override fun fetchSize(fetchSize: Int): KueryBlockingClient.FetchSpec =
            FetchSpec(sqlId, sql, fetchSize, maxRowsOpt, queryTimeoutSecondsOpt)

        override fun maxRows(maxRows: Int): KueryBlockingClient.FetchSpec =
            FetchSpec(sqlId, sql, fetchSizeOpt, maxRows, queryTimeoutSecondsOpt)

        override fun queryTimeoutSeconds(queryTimeoutSeconds: Int): KueryBlockingClient.FetchSpec =
            FetchSpec(sqlId, sql, fetchSizeOpt, maxRowsOpt, queryTimeoutSeconds)

        private fun buildSpec(): StatementSpec {
            var s = jdbcClient.sql(sql)
            if (fetchSizeOpt != null) s = s.withFetchSize(fetchSizeOpt)
            if (maxRowsOpt != null) s = s.withMaxRows(maxRowsOpt)
            if (queryTimeoutSecondsOpt != null) s = s.withQueryTimeout(queryTimeoutSecondsOpt)
            return s
        }

        override fun singleMap(): Map<String, Any?> = observe {
            buildSpec().query().singleRow()
        }

        override fun singleMapOrNull(): Map<String, Any?>? = observe {
            DataAccessUtils.singleResult(buildSpec().query().listOfRows())
        }

        override fun <T : Any> single(returnType: KClass<T>): T = observe {
            buildSpec().queryType(returnType).single()
        }

        override fun <T : Any> singleOrNull(returnType: KClass<T>): T? = observe {
            DataAccessUtils.singleResult(buildSpec().queryType(returnType).list())
        }

        override fun listMap(): List<Map<String, Any?>> = observe {
            buildSpec().query().listOfRows()
        }

        override fun <T : Any> list(returnType: KClass<T>): List<T> = observe {
            buildSpec().queryType(returnType).list()
        }

        override fun sequenceMap(): CloseableSequence<Map<String, Any?>> {
            // TODO:
            // I also want to measure the observation of flow.
            // However, should it be the time until the flow terminates or the time until the first element is obtained?
            // There are many uncertainties, so I will not implement it for now.
            //
            // The underlying statement is executed eagerly inside stream(), so wrapping it with
            // SqlIdInjector is enough for SpringJdbcKueryClient.sqlId() to be visible at execution time.
            return SqlIdInjector(sqlId).use {
                buildSpec().query(ColumnMapRowMapper()).stream().asCloseableSequence()
            }
        }

        override fun <T : Any> sequence(returnType: KClass<T>): CloseableSequence<T> {
            // TODO:
            // I also want to measure the observation of flow.
            // However, should it be the time until the flow terminates or the time until the first element is obtained?
            // There are many uncertainties, so I will not implement it for now.
            //
            // The underlying statement is executed eagerly inside stream(), so wrapping it with
            // SqlIdInjector is enough for SpringJdbcKueryClient.sqlId() to be visible at execution time.
            return SqlIdInjector(sqlId).use {
                buildSpec().queryType(returnType).stream().asCloseableSequence()
            }
        }

        override fun rowsUpdated(): Long = observe {
            buildSpec().update().toLong()
        }

        override fun generatedValues(vararg columns: String): Map<String, Any> = observe {
            val keyHolder = GeneratedKeyHolder()
            val spec = buildSpec()
            if (columns.isEmpty()) {
                spec.update(keyHolder)
            } else {
                spec.update(keyHolder, *columns)
            }
            // Align the exception types with the R2DBC module: EmptyResultDataAccessException when no
            // keys were generated (e.g. non-INSERT), IncorrectResultSizeDataAccessException for multi-row.
            DataAccessUtils.requiredSingleResult(keyHolder.keyList)
        }

        private fun <T> observe(block: () -> T): T {
            return SqlIdInjector(sqlId).use {
                val observation = observationOrNull() ?: return block()
                // Runs start/openScope/error/stop in the documented Micrometer lifecycle order
                // (the scope is closed before the observation is stopped).
                observation.observeChecked<T, Throwable>(block)
            }
        }

        private fun observationOrNull(): Observation? {
            if (observationRegistry == null) {
                return null
            }
            return KueryClientObservationDocumentation.FETCH.observation(
                observationConvention,
                defaultObservationConvention,
                { KueryClientFetchContext(sqlId, sql) },
                observationRegistry,
            )
        }

        private fun <T : Any> StatementSpec.queryType(returnType: KClass<T>): MappedQuerySpec<T> {
            val cs = conversionService

            @Suppress("UNCHECKED_CAST")
            val mapper = rowMapperCache.computeIfAbsent(returnType) {
                when {
                    // Checked before isSimpleProperty: a value class implementing e.g.
                    // CharSequence would otherwise be misrouted to the simple-type path.
                    // Spring's DataClassRowMapper cannot handle value classes; take over only
                    // for those cases and leave everything else on the Spring mapper.
                    returnType.isValue -> ValueClassScalarRowMapper(returnType, cs)
                    // Align with Spring Data JDBC's [DefaultJdbcClient] behavior
                    BeanUtils.isSimpleProperty(returnType.java) -> SingleColumnRowMapper(returnType.java).apply {
                        setConversionService(cs)
                    }
                    hasValueClassConstructorParameters(returnType) -> ValueClassPropertyRowMapper(returnType, cs)
                    else -> DataClassRowMapper(returnType.java).apply {
                        setConversionService(cs)
                    }
                }
            } as RowMapper<T>
            return this.query(mapper)
        }
    }
}
