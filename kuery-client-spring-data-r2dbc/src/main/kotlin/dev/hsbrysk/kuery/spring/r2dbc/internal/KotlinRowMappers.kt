@file:OptIn(KueryClientInternalApi::class)

package dev.hsbrysk.kuery.spring.r2dbc.internal

import dev.hsbrysk.kuery.core.KueryClientInternalApi
import dev.hsbrysk.kuery.spring.data.internal.ValueClassColumnConverter
import dev.hsbrysk.kuery.spring.data.internal.callConstructor
import dev.hsbrysk.kuery.spring.data.internal.hasValueClassConstructorParameters
import io.r2dbc.spi.Readable
import io.r2dbc.spi.ReadableMetadata
import org.springframework.beans.TypeConverter
import org.springframework.core.ResolvableType
import org.springframework.core.convert.ConversionService
import org.springframework.core.convert.TypeDescriptor
import org.springframework.dao.DataRetrievalFailureException
import org.springframework.r2dbc.core.DataClassRowMapper
import java.util.function.Function
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaType

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
} catch (@Suppress("SwallowedException") ex: IllegalArgumentException) {
    // The R2DBC SPI signals "the column cannot be decoded to the requested type" as
    // IllegalArgumentException — the same signal SingleColumnRowMapper falls back on. This is the
    // only failure that should degrade to the raw value; anything else (a driver/connection error,
    // an IndexOutOfBoundsException) is an unexpected fault that must propagate, not be masked here.
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

        // Whether the property (the outer position) is declared nullable.
        private val outerNullable = parameter.type.isMarkedNullable

        fun convert(
            raw: Any?,
            tc: TypeConverter,
            retrieveTyped: (Class<*>) -> Any?,
        ): Any? = when {
            target == null -> raw
            // Outer-priority: a nullable property takes SQL NULL as the outer null, not into the
            // value class. This also decides the doubly-nullable case (nullable property + nullable
            // underlying), where SQL NULL is ambiguous, in favour of the outer null.
            valueClassConverter != null && raw == null && outerNullable -> null
            valueClassConverter != null -> valueClassConverter.convert(raw, retrieveTyped)
            else -> tc.convertIfNecessary(raw, target.javaObjectType, typeDescriptor)
        }
    }
}
