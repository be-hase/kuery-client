package dev.hsbrysk.kuery.spring.r2dbc.internal

import dev.hsbrysk.kuery.core.KueryClient
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
) : KueryClient {
    override fun sql(block: SqlDsl.() -> Unit): KueryClient.FetchSpec {
        return DefaultFetchSpec(block.id(), databaseClient.sql(block))
    }

    @Suppress("TooManyFunctions")
    inner class DefaultFetchSpec(
        private val sqlId: String,
        private val spec: GenericExecuteSpec,
    ) : KueryClient.FetchSpec {
        override suspend fun single(): Map<String, Any?> {
            return spec.fetch().one().sqlId(sqlId).awaitSingleOrNull() ?: throw EmptyResultDataAccessException(1)
        }

        override suspend fun <T : Any> single(returnType: KClass<T>): T {
            return spec.map(returnType).one().sqlId(sqlId).awaitSingleOrNull()
                ?: throw EmptyResultDataAccessException(1)
        }

        override suspend fun singleOrNull(): Map<String, Any?>? {
            return spec.fetch().one().sqlId(sqlId).awaitSingleOrNull()
        }

        override suspend fun <T : Any> singleOrNull(returnType: KClass<T>): T? {
            return spec.map(returnType).one().sqlId(sqlId).awaitSingleOrNull()
        }

        override suspend fun list(): List<Map<String, Any?>> {
            return spec.fetch().all().collectList().sqlId(sqlId).awaitSingle()
        }

        override suspend fun <T : Any> list(returnType: KClass<T>): List<T> {
            return spec.map(returnType).all().collectList().sqlId(sqlId).awaitSingle()
        }

        override fun flow(): Flow<Map<String, Any?>> {
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
                .awaitSingleOrNull() ?: throw EmptyResultDataAccessException(1)
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

private fun DatabaseClient.sql(block: SqlDsl.() -> Unit): GenericExecuteSpec {
    val sql = Sql.create(block)
    @Suppress("SqlSourceToSinkFlow")
    return sql.parameters.fold(this.sql(sql.body)) { acc, parameter ->
        if (parameter.value != null) {
            acc.bindNull(parameter.name, parameter.kClass.java)
        } else {
            acc.bind(parameter.name, checkNotNull(parameter.value))
        }
    }
}
