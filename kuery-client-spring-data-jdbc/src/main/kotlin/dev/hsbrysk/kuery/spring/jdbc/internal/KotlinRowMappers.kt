package dev.hsbrysk.kuery.spring.jdbc.internal

import org.springframework.beans.TypeConverter
import org.springframework.core.ResolvableType
import org.springframework.core.convert.ConversionService
import org.springframework.core.convert.TypeDescriptor
import org.springframework.jdbc.IncorrectResultSetColumnCountException
import org.springframework.jdbc.core.DataClassRowMapper
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.support.JdbcUtils
import java.lang.reflect.InvocationTargetException
import java.sql.ResultSet
import java.sql.SQLException
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaType

// NOTE: The mapping logic in this file is intentionally duplicated in the r2dbc module
// (dev.hsbrysk.kuery.spring.r2dbc.internal.KotlinRowMappers); there is no shared
// Spring-dependent module to host it. Row access differs (ResultSet vs Readable) but the
// conversion rules must stay in sync.

/**
 * Whether [kClass] is a Kotlin class whose primary constructor takes at least one value class
 * parameter. Spring's DataClassRowMapper cannot instantiate such classes (it converts arguments
 * to the JVM-erased types but instantiates through Kotlin reflection, which expects boxed
 * values), so [ValueClassPropertyRowMapper] takes over. Java classes return false.
 */
internal fun hasValueClassConstructorParameters(kClass: KClass<*>): Boolean {
    val constructor = kClass.primaryConstructor ?: return false
    return constructor.parameters.any { (it.type.classifier as? KClass<*>)?.isValue == true }
}

/**
 * Maps a single-column row to a value class ("fetch type is the value class itself"). The column
 * value is retrieved as the (deepest) underlying type, converted, and boxed through the primary
 * constructor so `init` validation runs. A registered reading converter targeting the value
 * class takes precedence over automatic boxing. SQL NULL maps to a null row value.
 */
internal class ValueClassScalarRowMapper(
    kClass: KClass<*>,
    conversionService: ConversionService,
) : RowMapper<Any?> {
    private val converter = ValueClassColumnConverter(kClass, conversionService)

    override fun mapRow(
        rs: ResultSet,
        rowNum: Int,
    ): Any? {
        // Same contract as Spring's SingleColumnRowMapper: a scalar fetch requires exactly one column.
        val columnCount = rs.metaData.columnCount
        if (columnCount != 1) {
            throw IncorrectResultSetColumnCountException(1, columnCount)
        }
        val raw = retrieveValueClassColumn(rs, FIRST_COLUMN, converter.retrievalType)
        return converter.convert(raw)
    }

    companion object {
        private const val FIRST_COLUMN = 1
    }
}

/**
 * A [DataClassRowMapper] for Kotlin classes with value class constructor parameters, which the
 * Spring implementation cannot instantiate. Only the construction step is replaced: constructor
 * arguments are resolved with the inherited column matching (the lowercased parameter name, then
 * its underscored form) and the constructor is invoked through Kotlin reflection (`callBy`),
 * which handles value class boxing and, like `BeanUtils.instantiateClass`, applies Kotlin
 * default values by omitting null arguments for optional parameters. Everything else — value
 * retrieval, non-value-class argument conversion, and setter population of non-constructor
 * properties — is inherited from the Spring mapper.
 */
internal class ValueClassPropertyRowMapper(
    kClass: KClass<*>,
    private val kotlinConversionService: ConversionService,
) : DataClassRowMapper<Any>(
    @Suppress("UNCHECKED_CAST")
    (kClass.java as Class<Any>),
) {
    private val constructor = requireNotNull(kClass.primaryConstructor) {
        "$kClass has no primary constructor"
    }.apply {
        // Parity with Spring's mappers, which make non-public constructors accessible.
        isAccessible = true
    }
    private val parameters = constructor.parameters.map { ConstructorParameter(it) }

    init {
        conversionService = kotlinConversionService
    }

    override fun constructMappedInstance(
        rs: ResultSet,
        tc: TypeConverter,
    ): Any {
        val args = LinkedHashMap<KParameter, Any?>()
        for (parameter in parameters) {
            val index = findColumnIndex(rs, parameter.name)
            // Value class columns retrieve their (erased) underlying type; retry raw on failure
            // so a reading converter can handle a driver-incompatible column. Non-value-class
            // parameters keep Spring's TypeDescriptor-based conversion (full generics).
            val raw = if (parameter.isValueClass) {
                retrieveValueClassColumn(rs, index, parameter.retrievalType)
            } else {
                getColumnValue(rs, index, parameter.retrievalType)
            }
            val converted = parameter.convert(raw, tc)
            // Omit null arguments for optional parameters so Kotlin default values apply,
            // mirroring BeanUtils.instantiateClass.
            if (converted == null && parameter.parameter.isOptional) {
                continue
            }
            args[parameter.parameter] = converted
        }
        return callConstructor { constructor.callBy(args) }
    }

    private fun findColumnIndex(
        rs: ResultSet,
        name: String,
    ): Int = try {
        rs.findColumn(lowerCaseName(name))
    } catch (@Suppress("SwallowedException") ex: SQLException) {
        // Mirrors DataClassRowMapper: fall back to the underscored name; if that also fails,
        // the driver's exception propagates and is translated by Spring.
        rs.findColumn(underscoreName(name))
    }

    private inner class ConstructorParameter(val parameter: KParameter) {
        val name = checkNotNull(parameter.name)
        private val target = parameter.type.classifier as? KClass<*>
        private val valueClassConverter =
            if (target?.isValue == true) ValueClassColumnConverter(target, kotlinConversionService) else null
        val isValueClass = valueClassConverter != null
        val retrievalType: Class<*> = when {
            target == null -> Any::class.java
            valueClassConverter != null -> valueClassConverter.retrievalType
            else -> target.javaObjectType
        }

        // Carries the full generic type (e.g. List<MyEnum>) so element-wise conversion works,
        // like Spring's MethodParameter-based TypeDescriptors.
        private val typeDescriptor: TypeDescriptor? = if (valueClassConverter != null) {
            null
        } else {
            runCatching { TypeDescriptor(ResolvableType.forType(parameter.type.javaType), null, null) }.getOrNull()
        }

        fun convert(
            raw: Any?,
            tc: TypeConverter,
        ): Any? = when {
            target == null -> raw
            valueClassConverter != null -> valueClassConverter.convert(raw)
            else -> tc.convertIfNecessary(raw, target.javaObjectType, typeDescriptor)
        }
    }
}

// A value class's retrieval type is its (erased) underlying type, which the driver may be unable
// to produce for the column (e.g. a VARCHAR column mapped to a BigDecimal-underlying value class
// through a reading converter). Retry raw so the ConversionService/converter can handle it,
// mirroring the r2dbc mapper's IllegalArgumentException fallback.
private fun retrieveValueClassColumn(
    rs: ResultSet,
    index: Int,
    retrievalType: Class<*>,
): Any? = try {
    JdbcUtils.getResultSetValue(rs, index, retrievalType)
} catch (@Suppress("SwallowedException") ex: SQLException) {
    JdbcUtils.getResultSetValue(rs, index)
}

/**
 * Converts a column value to the value class [target]. The per-row work is minimized: the
 * converter-precedence decision is cached per source class (converter registrations are fixed
 * once the client is built), an already-assignable underlying value skips the ConversionService,
 * and a nested value class is handled by a recursive converter (so it shares the same caching and
 * boxing). The boxer is resolved lazily so a generic value class (whose boxing is unsupported)
 * can still be served by a registered reading converter, which wins before boxing is attempted.
 */
internal class ValueClassColumnConverter(
    target: KClass<*>,
    private val conversionService: ConversionService,
) {
    private val targetJavaType = target.javaObjectType

    // Driver retrieval hint: the fully-erased JVM type of the underlying value. Safe to resolve
    // eagerly even for a generic value class (unbox-impl erases to Object).
    val retrievalType: Class<*> = ValueClasses.underlyingType(target.java)

    private val boxer by lazy { boxers.get(target.java) }
    private val underlyingJavaType by lazy { boxer.underlying.javaObjectType }

    // Recursive converter for a nested value class, resolved lazily to share caching and boxing.
    private val underlyingConverter by lazy {
        boxer.underlying.takeIf { it.isValue }?.let { ValueClassColumnConverter(it, conversionService) }
    }

    private val canConvertCache = ConcurrentHashMap<Class<*>, Boolean>()

    fun convert(raw: Any?): Any? {
        if (raw == null) {
            return null
        }
        // A registered reading converter targeting the value class takes precedence over boxing.
        if (canConvertCache.computeIfAbsent(raw.javaClass) { conversionService.canConvert(it, targetJavaType) }) {
            return conversionService.convert(raw, targetJavaType)
        }
        // No converter: box. Resolving `boxer` here (lazily) means a generic value class without a
        // converter fails now, with the descriptive fail-fast message, rather than at construction.
        val underlying = when {
            underlyingJavaType.isInstance(raw) -> raw
            underlyingConverter != null -> underlyingConverter?.convert(raw)
            else -> conversionService.convert(raw, underlyingJavaType)
        }
        // A converter for the underlying type may legitimately return null (the Converter contract
        // allows it); map it to null instead of boxing null, which would fail for a non-null type.
        return underlying?.let { boxer.box(it) }
    }
}

private val boxers = object : ClassValue<ValueClassBoxer>() {
    override fun computeValue(type: Class<*>): ValueClassBoxer = ValueClassBoxer(type.kotlin)
}

// Caches the resolved (made-accessible) boxing machinery per value class; resolving the primary
// constructor through kotlin-reflect on every row is measurably slower. ClassValue ties entries
// to the class itself, so user classloaders are never pinned.
private class ValueClassBoxer(target: KClass<*>) {
    private val constructor = requireNotNull(target.primaryConstructor) {
        "$target has no primary constructor"
    }.apply {
        // Parity with Spring's mappers, which make non-public constructors accessible.
        isAccessible = true
    }

    // The underlying type of a generic value class is a type parameter, so the intended runtime
    // type is unknowable here; fail fast instead of constructing a heap-polluted instance. A
    // registered reading converter targeting the value class is the supported alternative (it
    // wins before boxing is attempted).
    val underlying: KClass<*> = requireNotNull(constructor.parameters.single().type.classifier as? KClass<*>) {
        "Cannot automatically box the generic value class $target: its underlying type is a type parameter. " +
            "Register a reading converter targeting $target instead."
    }

    // Fast path: the compiler-generated static `constructor-impl` (which contains the `init`
    // validation) + `box-impl` pair, invoked through plain Java reflection — measurably faster
    // than KFunction.call. They are part of a value class's stable JVM ABI (compiled callers in
    // other modules link against them). Disabled when the underlying is itself a value class:
    // `constructor-impl` takes the deeply-erased type, so boxing a not-yet-validated erased value
    // would bypass the inner value class's `init`; the Kotlin constructor path validates it.
    private val fastBox: ValueClasses.FastBox? =
        if (underlying.isValue) null else ValueClasses.fastBoxOrNull(target.java)

    /**
     * Boxes a non-null underlying value into the value class, unwrapping InvocationTargetException
     * so `init` validation failures surface directly, as they would on a regular constructor call.
     * A SQL NULL is mapped to a null value class before boxing, so this is never called with null.
     */
    fun box(value: Any): Any = callConstructor {
        val fast = fastBox
        if (fast != null && fast.accepts(value)) fast.box(value) else constructor.call(value)
    }
}

// Unwrap InvocationTargetException so `init` validation failures (e.g. IllegalArgumentException
// from require()) surface directly, as they would on a regular constructor call.
private fun callConstructor(block: () -> Any?): Any = try {
    checkNotNull(block())
} catch (ex: InvocationTargetException) {
    throw ex.targetException
}
