package dev.hsbrysk.kuery.spring.jdbc.internal

import dev.hsbrysk.kuery.core.KueryBlockingClient
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
import org.springframework.core.convert.ConversionService
import org.springframework.dao.support.DataAccessUtils
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions
import org.springframework.jdbc.core.DataClassRowMapper
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.SingleColumnRowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.core.simple.JdbcClient.MappedQuerySpec
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec
import org.springframework.jdbc.support.GeneratedKeyHolder
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

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
        return FetchSpec(sqlId, sql, jdbcClient.sql(sql))
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

    private fun StatementSpec.bind(parameter: NamedSqlParameter): StatementSpec {
        val value = checkNotNull(parameter.value)

        val targetType = customConversions.getCustomWriteTarget(value::class.java)
        if (targetType.isPresent) {
            return param(parameter.name, checkNotNull(conversionService.convert(value, targetType.get())))
        }

        return when (value) {
            is Collection<*> -> param(parameter.name, convertCollection(value))
            is Array<*> -> param(parameter.name, convertArray(value))
            is Enum<*> -> param(parameter.name, value.name)
            else -> param(parameter.name, value)
        }
    }

    private fun convertCollection(collection: Collection<*>): Collection<*> = collection.map {
        if (it == null) {
            null
        } else {
            val targetType = customConversions.getCustomWriteTarget(it::class.java)
            if (targetType.isPresent) {
                conversionService.convert(it, targetType.get())
            } else {
                it
            }
        }
    }

    private fun convertArray(array: Array<*>): Array<*> = array.map {
        if (it == null) {
            null
        } else {
            val targetType = customConversions.getCustomWriteTarget(it::class.java)
            if (targetType.isPresent) {
                conversionService.convert(it, targetType.get())
            } else {
                it
            }
        }
    }.toTypedArray()

    @Suppress("TooManyFunctions")
    inner class FetchSpec(
        private val sqlId: String,
        private val sql: Sql,
        private val spec: StatementSpec,
    ) : KueryBlockingClient.FetchSpec {
        override fun singleMap(): Map<String, Any?> = observe {
            spec.query().singleRow()
        }

        override fun singleMapOrNull(): Map<String, Any?>? = observe {
            DataAccessUtils.singleResult(spec.query().listOfRows())
        }

        override fun <T : Any> single(returnType: KClass<T>): T = observe {
            spec.queryType(returnType).single()
        }

        override fun <T : Any> singleOrNull(returnType: KClass<T>): T? = observe {
            DataAccessUtils.singleResult(spec.queryType(returnType).list())
        }

        override fun listMap(): List<Map<String, Any?>> = observe {
            spec.query().listOfRows()
        }

        override fun <T : Any> list(returnType: KClass<T>): List<T> = observe {
            spec.queryType(returnType).list()
        }

        override fun rowsUpdated(): Long = observe {
            spec.update().toLong()
        }

        override fun generatedValues(vararg columns: String): Map<String, Any> = observe {
            val keyHolder = GeneratedKeyHolder()
            if (columns.isEmpty()) {
                spec.update(keyHolder)
            } else {
                spec.update(keyHolder, *columns)
            }
            checkNotNull(keyHolder.keys)
        }

        private fun <T> observe(block: () -> T): T {
            return SqlIdInjector(sqlId).use {
                val observation = observationOrNull() ?: return block()

                observation.start()
                observation.openScope().use {
                    try {
                        block()
                    } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
                        observation.error(e)
                        throw e
                    } finally {
                        observation.stop()
                    }
                }
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
                // Align with Spring Data JDBC's [DefaultJdbcClient] behavior
                if (BeanUtils.isSimpleProperty(returnType.java)) {
                    SingleColumnRowMapper(returnType.java).apply {
                        setConversionService(cs)
                    }
                } else {
                    DataClassRowMapper(returnType.java).apply {
                        setConversionService(cs)
                    }
                }
            } as RowMapper<T>
            return this.query(mapper)
        }
    }
}
