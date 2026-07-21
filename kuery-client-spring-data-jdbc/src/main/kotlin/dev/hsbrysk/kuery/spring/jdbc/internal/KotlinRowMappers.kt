@file:OptIn(KueryClientInternalApi::class)

package dev.hsbrysk.kuery.spring.jdbc.internal

import dev.hsbrysk.kuery.core.KueryClientInternalApi
import dev.hsbrysk.kuery.spring.data.internal.ValueClassColumnConverter
import dev.hsbrysk.kuery.spring.data.internal.callConstructor
import dev.hsbrysk.kuery.spring.data.internal.hasValueClassConstructorParameters
import org.springframework.beans.TypeConverter
import org.springframework.core.ResolvableType
import org.springframework.core.convert.ConversionService
import org.springframework.core.convert.TypeDescriptor
import org.springframework.jdbc.IncorrectResultSetColumnCountException
import org.springframework.jdbc.core.DataClassRowMapper
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.support.JdbcUtils
import java.sql.ResultSet
import java.sql.SQLException
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaType

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
        val raw = JdbcUtils.getResultSetValue(rs, FIRST_COLUMN)
        return converter.convert(raw) { type -> retrieveTyped(rs, FIRST_COLUMN, type, raw) }
    }

    companion object {
        private const val FIRST_COLUMN = 1
    }
}

// Re-reads the column as [type], returning [raw] if the driver cannot produce that type so the
// caller can fall back to the ConversionService.
private fun retrieveTyped(
    rs: ResultSet,
    index: Int,
    type: Class<*>,
    raw: Any?,
): Any? = try {
    JdbcUtils.getResultSetValue(rs, index, type)
} catch (@Suppress("SwallowedException") ex: SQLException) {
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
            // Value class columns are retrieved raw (no type hint) so a reading converter keyed on
            // the column's own type wins over the driver coercing the value to the underlying type;
            // the boxing path converts the raw value to the underlying via the ConversionService.
            // Non-value-class parameters keep Spring's TypeDescriptor-based conversion (full generics).
            val raw = if (parameter.isValueClass) {
                JdbcUtils.getResultSetValue(rs, index)
            } else {
                getColumnValue(rs, index, parameter.retrievalType)
            }
            val converted = parameter.convert(raw, tc) { type -> retrieveTyped(rs, index, type, raw) }
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
