package dev.hsbrysk.kuery.spring.jdbc.internal

import dev.hsbrysk.kuery.core.KueryBlockingClient
import dev.hsbrysk.kuery.core.NamedSqlParameter
import dev.hsbrysk.kuery.core.Sql
import dev.hsbrysk.kuery.core.SqlDsl
import org.springframework.core.convert.ConversionService
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions
import org.springframework.jdbc.core.DataClassRowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.core.simple.JdbcClient.MappedQuerySpec
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec
import org.springframework.jdbc.support.GeneratedKeyHolder
import kotlin.reflect.KClass

internal class DefaultSpringJdbcKueryClient(
    private val jdbcClient: JdbcClient,
    private val conversionService: ConversionService,
    private val customConversions: JdbcCustomConversions,
) : KueryBlockingClient {
    override fun sql(block: SqlDsl.() -> Unit): KueryBlockingClient.FetchSpec {
        // TODO: block.id()
        return FetchSpec(jdbcClient.sql(block))
    }

    private fun JdbcClient.sql(block: SqlDsl.() -> Unit): StatementSpec {
        val sql = Sql.create(block)
        @Suppress("SqlSourceToSinkFlow")
        return sql.parameters.fold(this.sql(sql.body)) { acc, parameter ->
            if (parameter.value != null) {
                acc.bind(parameter)
            } else {
                acc.param(parameter.name, null)
            }
        }
    }

    private fun StatementSpec.bind(parameter: NamedSqlParameter<*>): StatementSpec {
        val value = checkNotNull(parameter.value)

        val targetType = customConversions.getCustomWriteTarget(value::class.java)
        if (targetType.isPresent) {
            return param(parameter.name, checkNotNull(conversionService.convert(value, targetType.get())))
        }

        if (value is Collection<*>) {
            return param(parameter.name, convertCollection(value))
        }

        if (value is Array<*>) {
            return param(parameter.name, convertArray(value))
        }

        if (value is Enum<*>) {
            return param(parameter.name, value.name)
        }

        return param(parameter.name, value)
    }

    private fun convertCollection(collection: Collection<*>): Collection<*> {
        return collection.map {
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
    }

    private fun convertArray(array: Array<*>): Array<*> {
        return array.map {
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
    }

    @Suppress("TooManyFunctions")
    inner class FetchSpec(
        private val spec: StatementSpec,
    ) : KueryBlockingClient.FetchSpec {
        override fun singleMap(): Map<String, Any?> {
            return spec.query().singleRow()
        }

        override fun singleMapOrNull(): Map<String, Any?>? {
            return try {
                spec.query().singleRow()
            } catch (e: EmptyResultDataAccessException) {
                null
            }
        }

        override fun <T : Any> single(returnType: KClass<T>): T {
            return spec.queryType(returnType).single()
        }

        override fun <T : Any> singleOrNull(returnType: KClass<T>): T? {
            return try {
                spec.queryType(returnType).single()
            } catch (e: EmptyResultDataAccessException) {
                null
            }
        }

        override fun listMap(): List<Map<String, Any?>> {
            return spec.query().listOfRows()
        }

        override fun <T : Any> list(returnType: KClass<T>): List<T> {
            return spec.queryType(returnType).list()
        }

        override fun rowsUpdated(): Long {
            return spec.update().toLong()
        }

        override fun generatedValues(vararg columns: String): Map<String, Any> {
            val keyHolder = GeneratedKeyHolder()
            spec.update(keyHolder, *columns)
            return keyHolder.keys!!
        }

        private fun <T : Any> StatementSpec.queryType(returnType: KClass<T>): MappedQuerySpec<T> {
            val cs = conversionService
            val mapper = DataClassRowMapper(returnType.java).apply {
                conversionService = cs
            }
            return this.query(mapper)
        }
    }
}
