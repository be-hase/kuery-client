package dev.hsbrysk.kuery.spring.r2dbc.internal

import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.core.NamedSqlParameter
import dev.hsbrysk.kuery.core.Sql
import dev.hsbrysk.kuery.core.SqlDsl
import dev.hsbrysk.kuery.core.id
import dev.hsbrysk.kuery.spring.r2dbc.SpringR2dbcKueryClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.core.convert.ConversionService
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions
import org.springframework.r2dbc.core.DataClassRowMapper
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec
import org.springframework.r2dbc.core.RowsFetchSpec
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.function.Function
import kotlin.reflect.KClass

internal class DefaultSpringR2dbcKueryClient(
    private val databaseClient: DatabaseClient,
    private val conversionService: ConversionService,
    private val customConversions: R2dbcCustomConversions,
) : KueryClient {
    override fun sql(block: SqlDsl.() -> Unit): KueryClient.FetchSpec {
        return FetchSpec(block.id(), databaseClient.sql(block))
    }

    private fun DatabaseClient.sql(block: SqlDsl.() -> Unit): GenericExecuteSpec {
        val sql = Sql.create(block)
        @Suppress("SqlSourceToSinkFlow")
        return sql.parameters.fold(this.sql(sql.body)) { acc, parameter ->
            if (parameter.value != null) {
                acc.bind(parameter)
            } else {
                acc.bindNull(parameter.name, Any::class.java)
            }
        }
    }

    private fun GenericExecuteSpec.bind(parameter: NamedSqlParameter<*>): GenericExecuteSpec {
        val value = checkNotNull(parameter.value)

        val targetType = customConversions.getCustomWriteTarget(value::class.java)
        if (targetType.isPresent) {
            return bind(parameter.name, checkNotNull(conversionService.convert(value, targetType.get())))
        }

        if (value is Collection<*>) {
            return bind(parameter.name, convertCollection(value))
        }

        if (value is Array<*>) {
            return bind(parameter.name, convertArray(value))
        }

        return bind(parameter.name, value)
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
        private val sqlId: String,
        private val spec: GenericExecuteSpec,
    ) : KueryClient.FetchSpec {
        override suspend fun singleMap(): Map<String, Any?> {
            return spec.fetch().one().sqlId(sqlId).awaitSingleOrNull() ?: throw EmptyResultDataAccessException(1)
        }

        override suspend fun singleMapOrNull(): Map<String, Any?>? {
            return spec.fetch().one().sqlId(sqlId).awaitSingleOrNull()
        }

        override suspend fun <T : Any> single(returnType: KClass<T>): T {
            return spec.map(returnType).one().sqlId(sqlId).awaitSingleOrNull()
                ?: throw EmptyResultDataAccessException(1)
        }

        override suspend fun <T : Any> singleOrNull(returnType: KClass<T>): T? {
            return spec.map(returnType).one().sqlId(sqlId).awaitSingleOrNull()
        }

        override suspend fun listMap(): List<Map<String, Any?>> {
            return spec.fetch().all().collectList().sqlId(sqlId).awaitSingle()
        }

        override suspend fun <T : Any> list(returnType: KClass<T>): List<T> {
            return spec.map(returnType).all().collectList().sqlId(sqlId).awaitSingle()
        }

        override fun flowMap(): Flow<Map<String, Any?>> {
            return spec.fetch().all().sqlId(sqlId).asFlow()
        }

        override fun <T : Any> flow(returnType: KClass<T>): Flow<T> {
            return spec.map(returnType).all().sqlId(sqlId).asFlow()
        }

        override suspend fun rowsUpdated(): Long {
            return spec.fetch().rowsUpdated().awaitSingle()
        }

        override suspend fun generatedValues(vararg columns: String): Map<String, Any> {
            return spec.filter(Function { it.returnGeneratedValues(*columns) }).fetch().one().sqlId(sqlId)
                .awaitSingleOrNull()
                ?: throw EmptyResultDataAccessException(1)
        }

        private fun <T> Mono<T>.sqlId(sqlId: String): Mono<T> {
            return contextWrite {
                it.put(SpringR2dbcKueryClient.SQL_ID_REACTOR_CONTEXT_KEY, sqlId)
            }
        }

        private fun <T> Flux<T>.sqlId(sqlId: String): Flux<T> {
            return contextWrite {
                it.put(SpringR2dbcKueryClient.SQL_ID_REACTOR_CONTEXT_KEY, sqlId)
            }
        }

        private fun <T : Any> GenericExecuteSpec.map(returnType: KClass<T>): RowsFetchSpec<T> {
            val mapper = DataClassRowMapper(returnType.java, conversionService)
            return this.map(mapper)
        }
    }
}
