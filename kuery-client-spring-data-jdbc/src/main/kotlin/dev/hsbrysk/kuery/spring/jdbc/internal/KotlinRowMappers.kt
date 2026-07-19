package dev.hsbrysk.kuery.spring.jdbc.internal

import org.springframework.core.convert.ConversionService
import org.springframework.jdbc.IncorrectResultSetColumnCountException
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.support.JdbcUtils
import java.lang.reflect.InvocationTargetException
import java.sql.ResultSet
import java.sql.SQLException
import java.util.Locale
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.primaryConstructor

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
    private val kClass: KClass<*>,
    private val conversionService: ConversionService,
) : RowMapper<Any?> {
    private val retrievalType = ValueClasses.underlyingType(kClass.java)

    override fun mapRow(
        rs: ResultSet,
        rowNum: Int,
    ): Any? {
        // Same contract as Spring's SingleColumnRowMapper: a scalar fetch requires exactly one column.
        val columnCount = rs.metaData.columnCount
        if (columnCount != 1) {
            throw IncorrectResultSetColumnCountException(1, columnCount)
        }
        val raw = JdbcUtils.getResultSetValue(rs, FIRST_COLUMN, retrievalType)
        return convertValue(raw, kClass, conversionService)
    }

    companion object {
        private const val FIRST_COLUMN = 1
    }
}

/**
 * A Kotlin-reflection based replacement for Spring's DataClassRowMapper, used only for classes
 * with value class constructor parameters. Column matching mirrors DataClassRowMapper: the
 * lowercased parameter name first, then its underscored form; an unresolvable column surfaces
 * as the driver's SQLException (translated to BadSqlGrammarException), like the Spring mapper.
 */
internal class ValueClassPropertyRowMapper(
    kClass: KClass<*>,
    private val conversionService: ConversionService,
) : RowMapper<Any> {
    private val constructor = requireNotNull(kClass.primaryConstructor) {
        "$kClass has no primary constructor"
    }

    override fun mapRow(
        rs: ResultSet,
        rowNum: Int,
    ): Any {
        val args = constructor.parameters.map { parameter ->
            val index = findColumnIndex(rs, checkNotNull(parameter.name))
            val target = parameter.type.classifier as? KClass<*>
            val retrievalType = target?.let {
                if (it.isValue) ValueClasses.underlyingType(it.java) else it.javaObjectType
            }
            val raw = JdbcUtils.getResultSetValue(rs, index, retrievalType)
            if (target != null) convertValue(raw, target, conversionService) else raw
        }
        return callConstructor(constructor, args)
    }

    private fun findColumnIndex(
        rs: ResultSet,
        name: String,
    ): Int = try {
        rs.findColumn(name.lowercase(Locale.US))
    } catch (@Suppress("SwallowedException") ex: SQLException) {
        // Mirrors DataClassRowMapper: fall back to the underscored name; if that also fails,
        // the driver's exception propagates and is translated by Spring.
        rs.findColumn(JdbcUtils.convertPropertyNameToUnderscoreName(name))
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
    }
    val underlying = constructor.parameters.single().type.classifier as? KClass<*>
    val converted = if (underlying != null) convertValue(raw, underlying, conversionService) else raw
    return callConstructor(constructor, listOf(converted))
}

// Unwrap InvocationTargetException so `init` validation failures (e.g. IllegalArgumentException
// from require()) surface directly, as they would on a regular constructor call.
private fun callConstructor(
    constructor: KFunction<*>,
    args: List<Any?>,
): Any = try {
    checkNotNull(constructor.call(*args.toTypedArray()))
} catch (ex: InvocationTargetException) {
    throw ex.targetException
}
