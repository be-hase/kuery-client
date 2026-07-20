package dev.hsbrysk.kuery.spring.r2dbc.internal

import dev.hsbrysk.kuery.core.KueryClient
import dev.hsbrysk.kuery.core.KueryClientInternalApi
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
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor
import io.r2dbc.spi.Readable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.BeanUtils
import org.springframework.core.KotlinDetector
import org.springframework.core.convert.ConversionService
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.dao.TypeMismatchDataAccessException
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions
import org.springframework.r2dbc.core.DataClassRowMapper
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function
import kotlin.reflect.KClass
import java.lang.reflect.Array as ReflectArray

@OptIn(KueryClientInternalApi::class)
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
        val sql = Sql(block)
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

    private fun GenericExecuteSpec.bind(parameter: NamedSqlParameter): GenericExecuteSpec =
        bindValue(parameter.name, checkNotNull(parameter.value))

    private fun GenericExecuteSpec.bindValue(
        name: String,
        value: Any,
    ): GenericExecuteSpec {
        val targetType = customConversions.getCustomWriteTarget(value::class.java)
        if (targetType.isPresent) {
            // The Converter contract allows a null result; bind it as SQL NULL of the target type.
            val converted = conversionService.convert(value, targetType.get())
            return if (converted != null) {
                bind(name, converted)
            } else {
                bindNull(name, targetType.get())
            }
        }

        if (KotlinDetector.isInlineClass(value.javaClass)) {
            // Unwrap and re-dispatch so the underlying value goes through the full conversion
            // pipeline again (custom converters, enums, nested value classes, ...).
            val unwrapped = ValueClasses.unbox(value)
                ?: run {
                    // The bindNull type must be what the driver would have received for a
                    // non-null value (enum -> String, custom write target, nested value class,
                    // boxed primitive array), not the raw underlying type, which may have no codec.
                    val underlying = ValueClasses.underlyingType(value.javaClass)
                    val bindNullType = componentWriteTarget(underlying)
                        ?: PRIMITIVE_ARRAY_TO_OBJECT_ARRAY[underlying]
                        ?: underlying
                    return bindNull(name, bindNullType)
                }
            return bindValue(name, unwrapped)
        }

        return when (value) {
            is Collection<*> -> bind(name, convertCollection(value))
            is Array<*> -> bind(name, convertArray(value))
            is Enum<*> -> bind(name, value.name)
            else -> bind(name, boxPrimitiveArray(value) ?: value)
        }
    }

    // R2DBC drivers' array codecs accept only object arrays, so box primitive arrays.
    // ByteArray is excluded: drivers encode byte[] as a single binary value, not an array.
    private fun boxPrimitiveArray(value: Any): Array<*>? = when (value) {
        is IntArray -> value.toTypedArray()
        is LongArray -> value.toTypedArray()
        is ShortArray -> value.toTypedArray()
        is DoubleArray -> value.toTypedArray()
        is FloatArray -> value.toTypedArray()
        is BooleanArray -> value.toTypedArray()
        is CharArray -> value.toTypedArray()
        else -> null
    }

    // NOTE: The conversion helpers below (convertCollection / convertArray / componentWriteTarget /
    // convertElement) are intentionally duplicated in DefaultSpringJdbcKueryClient; there is no
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
                // A generic value class erases its underlying to Object, which is no useful array
                // component type (drivers reject Object[]); return null so the caller infers the
                // concrete type from the unwrapped elements. Empty/all-null generic value class
                // arrays cannot be inferred and are left as Object[] (documented as unsupported).
                if (underlying == Any::class.java) null else componentWriteTarget(underlying) ?: underlying
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
        private val spec: GenericExecuteSpec,
    ) : KueryClient.FetchSpec {
        override fun fetchSize(fetchSize: Int): KueryClient.FetchSpec =
            FetchSpec(sqlId, sql, spec.filter(Function { it.fetchSize(fetchSize) }))

        override suspend fun singleMap(): Map<String, Any?> = spec.fetch().one()
            .switchIfEmpty(Mono.error { EmptyResultDataAccessException(1) })
            .sqlId(sqlId)
            .observe()
            .awaitSingle()

        override suspend fun singleMapOrNull(): Map<String, Any?>? = spec.fetch().one()
            .sqlId(sqlId)
            .observe()
            .awaitSingleOrNull()

        override suspend fun <T : Any> single(returnType: KClass<T>): T {
            val value = spec.map(mapper(returnType)).one()
                .switchIfEmpty(Mono.error { EmptyResultDataAccessException(1) })
                .sqlId(sqlId)
                .observe()
                .awaitSingle()
                .unwrapNullValue<T>()
            // Same exception as the JDBC client (DataAccessUtils.requiredSingleResult)
            return value ?: throw TypeMismatchDataAccessException("Result value is null but no null value expected")
        }

        override suspend fun <T : Any> singleOrNull(returnType: KClass<T>): T? = spec.map(mapper(returnType)).one()
            .sqlId(sqlId)
            .observe()
            .awaitSingleOrNull()
            ?.unwrapNullValue()

        override suspend fun listMap(): List<Map<String, Any?>> = spec.fetch().all().collectList()
            .sqlId(sqlId)
            .observe()
            .awaitSingle()

        override suspend fun <T : Any> list(returnType: KClass<T>): List<T> {
            val mapper = mapper(returnType)
            val values = spec.map(mapper).all().collectList()
                .sqlId(sqlId)
                .observe()
                .awaitSingle()
            // NULL scalar values are kept as null elements, like the JDBC client. The declared
            // element type cannot express this, but type erasure makes it observable the same way.
            @Suppress("UNCHECKED_CAST")
            return if (isScalarMapper(mapper)) {
                values.map { it.unwrapNullValue<T>() } as List<T>
            } else {
                values as List<T>
            }
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
            val mapper = mapper(returnType)
            val flow = spec.map(mapper).all().sqlId(sqlId).asFlow()
            @Suppress("UNCHECKED_CAST")
            return if (isScalarMapper(mapper)) {
                flow.map { it.unwrapNullValue<T>() } as Flow<T>
            } else {
                flow as Flow<T>
            }
        }

        override suspend fun rowsUpdated(): Long = spec.fetch().rowsUpdated()
            .sqlId(sqlId)
            .observe()
            .awaitSingle()

        override suspend fun generatedValues(vararg columns: String): Map<String, Any> =
            spec.filter(Function { it.returnGeneratedValues(*columns) }).fetch().one()
                .switchIfEmpty(Mono.error { EmptyResultDataAccessException(1) })
                .sqlId(sqlId)
                .observe()
                .awaitSingle()

        // Do NOT open a ThreadLocal-based Observation.Scope here. The caller suspends (awaitSingle etc.),
        // and an open scope would leak to unrelated coroutines running on the same thread while suspended.
        // Propagate the observation via the Reactor context instead, so downstream instrumentation
        // (r2dbc-proxy, Spring Boot's automatic context propagation) can restore it.
        // Same pattern as Spring's DefaultWebClient.exchange().
        //
        // The observation is stopped via usingWhen's cleanup handlers, which run BEFORE the terminal
        // signal propagates downstream — this guarantees the observation is already stopped when the
        // suspending caller resumes from awaitXxx.
        private fun <T : Any> Mono<T>.observe(): Mono<T> {
            val registry = observationRegistry ?: return this
            return Mono.deferContextual { contextView ->
                Mono.usingWhen(
                    Mono.fromSupplier {
                        KueryClientObservationDocumentation.FETCH
                            .observation(
                                observationConvention,
                                defaultObservationConvention,
                                { KueryClientFetchContext(sqlId, sql) },
                                registry,
                            )
                            .parentObservation(
                                contextView.getOrDefault<Observation>(ObservationThreadLocalAccessor.KEY, null),
                            )
                            .start()
                    },
                    { observation -> this.contextWrite { it.put(ObservationThreadLocalAccessor.KEY, observation) } },
                    { observation -> Mono.fromRunnable<Unit> { observation.stop() } },
                    { observation, e ->
                        Mono.fromRunnable<Unit> {
                            observation.error(e)
                            observation.stop()
                        }
                    },
                    { observation -> Mono.fromRunnable<Unit> { observation.stop() } },
                )
            }
        }

        private fun <T : Any> Mono<T>.sqlId(sqlId: String): Mono<T> = contextWrite {
            it.put(SpringR2dbcKueryClient.SQL_ID_REACTOR_CONTEXT_KEY, sqlId)
        }

        private fun <T : Any> Flux<T>.sqlId(sqlId: String): Flux<T> = contextWrite {
            it.put(SpringR2dbcKueryClient.SQL_ID_REACTOR_CONTEXT_KEY, sqlId)
        }

        // Only the scalar mappers ([SingleColumnRowMapper] / [ValueClassScalarRowMapper]) can emit
        // [NullValue] sentinels, which the terminal operators must unwrap; the data class paths
        // never do.
        private fun mapper(returnType: KClass<*>): Function<Readable, Any> {
            val mapper = mapperCache.computeIfAbsent(returnType) {
                when {
                    // Checked before isSimpleProperty: a value class implementing e.g.
                    // CharSequence would otherwise be misrouted to the simple-type path.
                    // Spring's DataClassRowMapper cannot handle value classes; take over only
                    // for those cases and leave everything else on the Spring mapper.
                    returnType.isValue -> ValueClassScalarRowMapper(returnType, conversionService)
                    BeanUtils.isSimpleProperty(returnType.java) ->
                        SingleColumnRowMapper(returnType.javaObjectType, conversionService)
                    hasValueClassConstructorParameters(returnType) ->
                        ValueClassPropertyRowMapper(returnType, conversionService)
                    else -> DataClassRowMapper(returnType.java, conversionService)
                }
            }
            @Suppress("UNCHECKED_CAST")
            return mapper as Function<Readable, Any>
        }

        private fun isScalarMapper(mapper: Function<Readable, Any>): Boolean =
            mapper is SingleColumnRowMapper || mapper is ValueClassScalarRowMapper
    }

    // A Readable-based counterpart of Spring JDBC's SingleColumnRowMapper:
    // https://github.com/spring-projects/spring-framework/blob/bf06d74879029593b40d3825aca39dad9f229f44/spring-jdbc/src/main/java/org/springframework/jdbc/core/SingleColumnRowMapper.java
    //
    // First asks the driver for the value as [requiredType]; when the driver cannot convert it, falls back to
    // [conversionService] (DefaultConversionService plus user-registered converters). Standard conversions such
    // as string-to-number and any-to-string are therefore supported, aligning the observable behavior with the
    // JDBC module. Which of the two paths handles a given conversion is driver-dependent.
    //
    // The R2DBC SPI forbids Result.map mapping functions from returning null, so a SQL NULL value
    // is returned as [NullValue] instead and unwrapped at the terminal operators.
    class SingleColumnRowMapper(
        private val requiredType: Class<*>,
        private val conversionService: ConversionService,
    ) : Function<Readable, Any> {
        override fun apply(readable: Readable): Any = try {
            readable.get(0, requiredType) ?: NullValue
        } catch (ignored: IllegalArgumentException) {
            val result = readable.get(0)
            when {
                result == null -> NullValue
                conversionService.canConvert(result.javaClass, requiredType) -> {
                    conversionService.convert(result, requiredType) ?: NullValue
                }
                else -> throw IllegalArgumentException(
                    "Value [$result] is of type [${result.javaClass.name}] and " +
                        "cannot be converted to required type [${requiredType.name}]",
                )
            }
        }
    }
}

// Maps a primitive array type to its boxed (object) array type, the type `boxPrimitiveArray`
// would produce for a non-null value. Used to pick the `bindNull` type for a value class
// wrapping a nullable primitive array. ByteArray is excluded: drivers encode byte[] as a single
// binary value, not an array.
private val PRIMITIVE_ARRAY_TO_OBJECT_ARRAY: Map<Class<*>, Class<*>> = mapOf(
    IntArray::class.java to Array<Int>::class.java,
    LongArray::class.java to Array<Long>::class.java,
    ShortArray::class.java to Array<Short>::class.java,
    DoubleArray::class.java to Array<Double>::class.java,
    FloatArray::class.java to Array<Float>::class.java,
    BooleanArray::class.java to Array<Boolean>::class.java,
    CharArray::class.java to Array<Char>::class.java,
)
