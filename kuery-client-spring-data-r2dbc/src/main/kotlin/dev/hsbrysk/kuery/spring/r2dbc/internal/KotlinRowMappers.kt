package dev.hsbrysk.kuery.spring.r2dbc.internal

import io.r2dbc.spi.Readable
import io.r2dbc.spi.ReadableMetadata
import org.springframework.beans.TypeConverter
import org.springframework.core.ResolvableType
import org.springframework.core.convert.ConversionService
import org.springframework.core.convert.TypeDescriptor
import org.springframework.dao.DataRetrievalFailureException
import org.springframework.r2dbc.core.DataClassRowMapper
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaType

// NOTE: The mapping logic in this file is intentionally duplicated in the jdbc module
// (dev.hsbrysk.kuery.spring.jdbc.internal.KotlinRowMappers); there is no shared
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
 * class takes precedence over automatic boxing. SQL NULL maps to the [NullValue] sentinel,
 * unwrapped at the terminal operators.
 */
internal class ValueClassScalarRowMapper(
    kClass: KClass<*>,
    conversionService: ConversionService,
) : Function<Readable, Any> {
    private val converter = ValueClassColumnConverter(kClass, conversionService)

    override fun apply(readable: Readable): Any {
        val raw = readable.get(FIRST_COLUMN)
        return converter.convert(raw) { type -> retrieveTyped(readable, FIRST_COLUMN, type, raw) } ?: NullValue
    }

    companion object {
        private const val FIRST_COLUMN = 0
    }
}

// Re-reads the column as [type], returning [raw] if the driver cannot produce that type so the
// caller can fall back to the ConversionService.
private fun retrieveTyped(
    readable: Readable,
    index: Int,
    type: Class<*>,
    raw: Any?,
): Any? = try {
    readable.get(index, type)
} catch (@Suppress("SwallowedException", "TooGenericExceptionCaught") ex: RuntimeException) {
    raw
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
    kotlinConversionService,
) {
    private val constructor = requireNotNull(kClass.primaryConstructor) {
        "$kClass has no primary constructor"
    }.apply {
        // Parity with Spring's mappers, which make non-public constructors accessible.
        isAccessible = true
    }
    private val parameters = constructor.parameters.map { ConstructorParameter(it) }

    override fun constructMappedInstance(
        readable: Readable,
        itemMetadatas: List<ReadableMetadata>,
        tc: TypeConverter,
    ): Any {
        val args = LinkedHashMap<KParameter, Any?>()
        for (parameter in parameters) {
            val index = findColumnIndex(itemMetadatas, parameter)
            // Value class columns are retrieved raw (no type hint) so a reading converter keyed on
            // the column's own type wins over the driver coercing the value to the underlying type;
            // the boxing path converts the raw value to the underlying via the ConversionService.
            // Non-value-class parameters keep Spring's TypeDescriptor-based conversion (full generics).
            val raw = if (parameter.isValueClass) {
                readable.get(index)
            } else {
                getItemValue(readable, index, parameter.retrievalType)
            }
            val converted = parameter.convert(raw, tc) { type -> retrieveTyped(readable, index, type, raw) }
            // Omit null arguments for optional parameters so Kotlin default values apply,
            // mirroring BeanUtils.instantiateClass.
            if (converted == null && parameter.parameter.isOptional) {
                continue
            }
            args[parameter.parameter] = converted
        }
        return callConstructor { constructor.callBy(args) }
    }

    // Mirrors the private findIndex logic in Spring's DataClassRowMapper, using the inherited
    // name-mangling helpers (precomputed per parameter).
    private fun findColumnIndex(
        itemMetadatas: List<ReadableMetadata>,
        parameter: ConstructorParameter,
    ): Int {
        itemMetadatas.forEachIndexed { index, metadata ->
            if (metadata.name.equals(parameter.lowerCasedName, ignoreCase = true)) {
                return index
            }
        }
        itemMetadatas.forEachIndexed { index, metadata ->
            if (metadata.name.equals(parameter.underscoredName, ignoreCase = true)) {
                return index
            }
        }
        throw DataRetrievalFailureException(
            "Unable to map constructor parameter [${parameter.name}] to a column " +
                "(tried [${parameter.lowerCasedName}] and [${parameter.underscoredName}])",
        )
    }

    private inner class ConstructorParameter(val parameter: KParameter) {
        val name = checkNotNull(parameter.name)
        val lowerCasedName: String = lowerCaseName(name)
        val underscoredName: String = underscoreName(name)
        private val target = parameter.type.classifier as? KClass<*>
        private val valueClassConverter =
            if (target?.isValue == true) ValueClassColumnConverter(target, kotlinConversionService) else null
        val isValueClass = valueClassConverter != null

        // Retrieval hint for the non-value-class (Spring TypeDescriptor) path only; value class
        // columns are retrieved raw.
        val retrievalType: Class<*> = target?.javaObjectType ?: Any::class.java

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
            retrieveTyped: (Class<*>) -> Any?,
        ): Any? = when {
            target == null -> raw
            valueClassConverter != null -> valueClassConverter.convert(raw, retrieveTyped)
            else -> tc.convertIfNecessary(raw, target.javaObjectType, typeDescriptor)
        }
    }
}

/**
 * Converts a raw column value to the value class [target]. The per-row work is minimized: the
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

    private val boxer by lazy { boxers.get(target.java) }
    private val underlyingJavaType by lazy { boxer.underlying.javaObjectType }

    // Recursive converter for a nested value class, resolved lazily to share caching and boxing.
    private val underlyingConverter by lazy {
        boxer.underlying.takeIf { it.isValue }?.let { ValueClassColumnConverter(it, conversionService) }
    }

    private val canConvertCache = ConcurrentHashMap<Class<*>, Boolean>()

    /**
     * Converts the [raw] column value (retrieved with no type hint) to the value class.
     * [retrieveTyped] re-reads the same column asking the driver for a given type; it is used to
     * preserve driver coercions (e.g. a numeric column to a `Boolean` underlying) that the
     * ConversionService cannot perform, and must return the raw value if the driver cannot produce
     * the requested type.
     */
    fun convert(
        raw: Any?,
        retrieveTyped: (Class<*>) -> Any?,
    ): Any? {
        if (raw == null) {
            return null
        }
        // A registered reading converter keyed on the raw column type takes precedence over boxing
        // (and over the driver's coercion to the underlying type).
        if (canConvertCache.computeIfAbsent(raw.javaClass) { conversionService.canConvert(it, targetJavaType) }) {
            return conversionService.convert(raw, targetJavaType)
        }
        // No converter: box. Resolving `boxer` here (lazily) means a generic value class without a
        // converter fails now, with the descriptive fail-fast message, rather than at construction.
        val underlying = when {
            underlyingJavaType.isInstance(raw) -> raw
            underlyingConverter != null -> underlyingConverter?.convert(raw, retrieveTyped)
            else -> coerceToUnderlying(raw, retrieveTyped)
        }
        // A converter for the underlying type may legitimately return null (the Converter contract
        // allows it); map it to null instead of boxing null, which would fail for a non-null type.
        return underlying?.let { boxer.box(it) }
    }

    // Prefer the driver's typed retrieval of the underlying value (it coerces where the
    // ConversionService cannot, e.g. a numeric column to a Boolean underlying); if the driver
    // produced a different type (e.g. a String for an enum underlying) or could not produce the
    // requested type, convert it through the ConversionService.
    private fun coerceToUnderlying(
        raw: Any,
        retrieveTyped: (Class<*>) -> Any?,
    ): Any? {
        val candidate = retrieveTyped(underlyingJavaType) ?: raw
        return if (underlyingJavaType.isInstance(candidate)) {
            candidate
        } else {
            conversionService.convert(candidate, underlyingJavaType)
        }
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
