package dev.hsbrysk.kuery.spring.r2dbc.internal

import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.core.NamedSqlParameter
import dev.hsbrysk.kuery.core.Sql
import dev.hsbrysk.kuery.core.SqlBuilder
import dev.hsbrysk.kuery.core.internal.SqlIds.id
import dev.hsbrysk.kuery.core.observation.KueryClientFetchContext
import dev.hsbrysk.kuery.core.observation.KueryClientFetchObservationConvention
import dev.hsbrysk.kuery.core.observation.KueryClientObservationDocumentation
import dev.hsbrysk.kuery.spring.r2dbc.SpringR2dbcKueryClient
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import io.r2dbc.spi.Readable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.BeanUtils
import org.springframework.core.convert.ConversionService
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions
import org.springframework.r2dbc.core.DataClassRowMapper
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec
import org.springframework.r2dbc.core.RowsFetchSpec
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function
import kotlin.reflect.KClass

internal class DefaultSpringR2dbcKueryClient(
    private val databaseClient: DatabaseClient,
    private val conversionService: ConversionService,
    private val customConversions: R2dbcCustomConversions,
    private val observationRegistry: ObservationRegistry?,
    private val observationConvention: KueryClientFetchObservationConvention?,
    private val enableAutoSqlIdGeneration: Boolean,
) : KueryClient {
    private val defaultObservationConvention = KueryClientFetchObservationConvention.default()
    private val mapperCache = ConcurrentHashMap<KClass<*>, Function<Readable, *>>()

    override fun sql(
        sqlId: String,
        block: SqlBuilder.() -> Unit,
    ): KueryClient.FetchSpec {
        val sql = Sql.create(block)
        return FetchSpec(sqlId, sql, databaseClient.sql(sql))
    }

    override fun sql(block: SqlBuilder.() -> Unit): KueryClient.FetchSpec {
        val sqlId = if (enableAutoSqlIdGeneration) block.id() else "NONE"
        return sql(sqlId, block)
    }

    private fun DatabaseClient.sql(sql: Sql): GenericExecuteSpec = sql.parameters.fold(this.sql(sql.body)) {
            acc,
            parameter,
        ->
        if (parameter.value != null) {
            acc.bind(parameter)
        } else {
            acc.bindNull(parameter.name, Any::class.java)
        }
    }

    private fun GenericExecuteSpec.bind(parameter: NamedSqlParameter): GenericExecuteSpec {
        val value = checkNotNull(parameter.value)

        val targetType = customConversions.getCustomWriteTarget(value::class.java)
        if (targetType.isPresent) {
            return bind(parameter.name, checkNotNull(conversionService.convert(value, targetType.get())))
        }

        return when (value) {
            is Collection<*> -> bind(parameter.name, convertCollection(value))
            is Array<*> -> bind(parameter.name, convertArray(value))
            is Enum<*> -> bind(parameter.name, value.name)
            else -> bind(parameter.name, value)
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
        private val spec: GenericExecuteSpec,
    ) : KueryClient.FetchSpec {
        override suspend fun singleMap(): Map<String, Any?> = observe {
            spec.fetch().one().sqlId(sqlId).awaitSingleOrNull() ?: throw EmptyResultDataAccessException(1)
        }

        override suspend fun singleMapOrNull(): Map<String, Any?>? = observe {
            spec.fetch().one().sqlId(sqlId).awaitSingleOrNull()
        }

        override suspend fun <T : Any> single(returnType: KClass<T>): T = observe {
            spec.map(returnType).one().sqlId(sqlId).awaitSingleOrNull() ?: throw EmptyResultDataAccessException(1)
        }

        override suspend fun <T : Any> singleOrNull(returnType: KClass<T>): T? = observe {
            spec.map(returnType).one().sqlId(sqlId).awaitSingleOrNull()
        }

        override suspend fun listMap(): List<Map<String, Any?>> = observe {
            spec.fetch().all().collectList().sqlId(sqlId).awaitSingle()
        }

        override suspend fun <T : Any> list(returnType: KClass<T>): List<T> = observe {
            spec.map(returnType).all().collectList().sqlId(sqlId).awaitSingle()
        }

        override fun flowMap(): Flow<Map<String, Any?>> {
            // TODO:
            // I also want to measure the observation of flow.
            // However, should it be the time until the flow terminates or the time until the first element is obtained?
            // There are many uncertainties, so I will not implement it for now.
            return spec.fetch().all().sqlId(sqlId).asFlow()
        }

        override fun <T : Any> flow(returnType: KClass<T>): Flow<T> {
            // TODO:
            // I also want to measure the observation of flow.
            // However, should it be the time until the flow terminates or the time until the first element is obtained?
            // There are many uncertainties, so I will not implement it for now.
            return spec.map(returnType).all().sqlId(sqlId).asFlow()
        }

        override suspend fun rowsUpdated(): Long = observe {
            spec.fetch().rowsUpdated().awaitSingle()
        }

        override suspend fun generatedValues(vararg columns: String): Map<String, Any> = observe {
            spec.filter(Function { it.returnGeneratedValues(*columns) }).fetch().one().sqlId(sqlId).awaitSingleOrNull()
                ?: throw EmptyResultDataAccessException(1)
        }

        private suspend fun <T> observe(block: suspend () -> T): T {
            val observation = observationOrNull() ?: return block()

            observation.start()
            return observation.openScope().use {
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

        private fun <T> Mono<T>.sqlId(sqlId: String): Mono<T> = contextWrite {
            it.put(SpringR2dbcKueryClient.SQL_ID_REACTOR_CONTEXT_KEY, sqlId)
        }

        private fun <T> Flux<T>.sqlId(sqlId: String): Flux<T> = contextWrite {
            it.put(SpringR2dbcKueryClient.SQL_ID_REACTOR_CONTEXT_KEY, sqlId)
        }

        private fun <T : Any> GenericExecuteSpec.map(returnType: KClass<T>): RowsFetchSpec<T> {
            @Suppress("UNCHECKED_CAST")
            val mapper = mapperCache.computeIfAbsent(returnType) {
                if (BeanUtils.isSimpleProperty(returnType.java)) {
                    singleColumnRowMapper(returnType.javaObjectType)
                } else {
                    DataClassRowMapper(returnType.java, conversionService)
                }
            } as Function<Readable, T>
            return this.map(mapper)
        }

        // ref: https://github.com/spring-projects/spring-framework/blob/bf06d74879029593b40d3825aca39dad9f229f44/spring-jdbc/src/main/java/org/springframework/jdbc/core/SingleColumnRowMapper.java
        // However, conversions such as any-to-string or string-to-number are intentionally not implemented.
        private fun <T : Any> singleColumnRowMapper(requiredType: Class<T>) = Function { readable: Readable ->
            try {
                readable.get(0, requiredType)
            } catch (e: IllegalArgumentException) {
                val result = readable.get(0)
                when {
                    conversionService.canConvert(result?.javaClass, requiredType) -> {
                        conversionService.convert(result, requiredType)
                    }
                    else -> throw IllegalArgumentException(
                        "Value [$result] is of type [${result?.javaClass?.name}] and " + "cannot be converted to required type [${requiredType.name}]",
                    )
                }
            }
        }
    }
}
