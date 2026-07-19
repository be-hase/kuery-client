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
    private val kClass: KClass<*>,
    private val conversionService: ConversionService,
) : Function<Readable, Any> {
    private val retrievalType = ValueClasses.underlyingType(kClass.java)

    override fun apply(readable: Readable): Any {
        val raw = try {
            readable.get(FIRST_COLUMN, retrievalType)
        } catch (@Suppress("SwallowedException") ex: IllegalArgumentException) {
            // The driver cannot convert to the underlying type; retry raw and let
            // [convertValue] handle it through the ConversionService.
            readable.get(FIRST_COLUMN)
        }
        return convertValue(raw, kClass, conversionService) ?: NullValue
    }

    companion object {
        private const val FIRST_COLUMN = 0
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
            val index = findColumnIndex(itemMetadatas, parameter.name)
            val raw = getItemValue(readable, index, parameter.retrievalType)
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

    // Mirrors the private findIndex logic in Spring's DataClassRowMapper, using the inherited
    // name-mangling helpers.
    private fun findColumnIndex(
        itemMetadatas: List<ReadableMetadata>,
        name: String,
    ): Int {
        val lowerCased = lowerCaseName(name)
        val underscored = underscoreName(name)
        itemMetadatas.forEachIndexed { index, metadata ->
            if (metadata.name.equals(lowerCased, ignoreCase = true)) {
                return index
            }
        }
        itemMetadatas.forEachIndexed { index, metadata ->
            if (metadata.name.equals(underscored, ignoreCase = true)) {
                return index
            }
        }
        throw DataRetrievalFailureException(
            "Unable to map constructor parameter [$name] to a column (tried [$lowerCased] and [$underscored])",
        )
    }

    private inner class ConstructorParameter(val parameter: KParameter) {
        val name = checkNotNull(parameter.name)
        private val target = parameter.type.classifier as? KClass<*>
        private val isValueClass = target?.isValue == true
        val retrievalType: Class<*> = when {
            target == null -> Any::class.java
            target.isValue -> ValueClasses.underlyingType(target.java)
            else -> target.javaObjectType
        }

        // Carries the full generic type (e.g. List<MyEnum>) so element-wise conversion works,
        // like Spring's MethodParameter-based TypeDescriptors.
        private val typeDescriptor: TypeDescriptor? = if (isValueClass) {
            null
        } else {
            runCatching { TypeDescriptor(ResolvableType.forType(parameter.type.javaType), null, null) }.getOrNull()
        }

        fun convert(
            raw: Any?,
            tc: TypeConverter,
        ): Any? = when {
            target == null -> raw
            isValueClass -> convertValue(raw, target, kotlinConversionService)
            else -> tc.convertIfNecessary(raw, target.javaObjectType, typeDescriptor)
        }
    }
}

/**
 * Converts a column value to a constructor argument of type [target].
 *
 * Order: null passes through; an already-assignable value is used as is; a registered converter
 * (or standard conversion, e.g. String -> enum) wins; a value class is built by recursively
 * converting to its underlying type and boxing through the primary constructor.
 */
internal fun convertValue(
    raw: Any?,
    target: KClass<*>,
    conversionService: ConversionService,
): Any? {
    if (raw == null) {
        return null
    }
    val javaType = target.javaObjectType
    return when {
        !target.isValue && javaType.isInstance(raw) -> raw
        conversionService.canConvert(raw.javaClass, javaType) -> conversionService.convert(raw, javaType)
        target.isValue -> boxValueClass(raw, target, conversionService)
        // Let the ConversionService raise its descriptive ConverterNotFoundException.
        else -> conversionService.convert(raw, javaType)
    }
}

private fun boxValueClass(
    raw: Any,
    target: KClass<*>,
    conversionService: ConversionService,
): Any {
    val constructor = requireNotNull(target.primaryConstructor) {
        "$target has no primary constructor"
    }.apply {
        // Parity with Spring's mappers, which make non-public constructors accessible.
        isAccessible = true
    }
    // The underlying type of a generic value class is a type parameter, so the intended runtime
    // type is unknowable here; fail fast instead of constructing a heap-polluted instance. A
    // registered reading converter targeting the value class is the supported alternative (it
    // wins before boxing is attempted).
    val underlying = requireNotNull(constructor.parameters.single().type.classifier as? KClass<*>) {
        "Cannot automatically box the generic value class $target: its underlying type is a type parameter. " +
            "Register a reading converter targeting $target instead."
    }
    val converted = convertValue(raw, underlying, conversionService)
    return callConstructor { constructor.call(converted) }
}

// Unwrap InvocationTargetException so `init` validation failures (e.g. IllegalArgumentException
// from require()) surface directly, as they would on a regular constructor call.
private fun callConstructor(block: () -> Any?): Any = try {
    checkNotNull(block())
} catch (ex: InvocationTargetException) {
    throw ex.targetException
}
