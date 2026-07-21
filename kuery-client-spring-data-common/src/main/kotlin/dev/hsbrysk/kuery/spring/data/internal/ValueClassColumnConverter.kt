@file:OptIn(KueryClientInternalApi::class)

package dev.hsbrysk.kuery.spring.data.internal

import dev.hsbrysk.kuery.core.KueryClientInternalApi
import org.springframework.core.convert.ConversionService
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible

/**
 * Whether [kClass] is a Kotlin class whose primary constructor takes at least one value class
 * parameter. Spring's DataClassRowMapper cannot instantiate such classes because it converts
 * arguments to JVM-erased types but instantiates through Kotlin reflection, which expects boxed
 * values. Java classes return false.
 */
@KueryClientInternalApi
public fun hasValueClassConstructorParameters(kClass: KClass<*>): Boolean {
    val constructor = kClass.primaryConstructor ?: return false
    return constructor.parameters.any { (it.type.classifier as? KClass<*>)?.isValue == true }
}

/**
 * Converts a raw column value to the value class [target]. The per-row work is minimized: the
 * converter-precedence decision is cached per source class (converter registrations are fixed
 * once the client is built), an already-assignable underlying value skips the ConversionService,
 * and a nested value class is handled by a recursive converter (so it shares the same caching and
 * boxing). The boxer is resolved lazily so a generic value class (whose boxing is unsupported)
 * can still be served by a registered reading converter, which wins before boxing is attempted.
 */
@KueryClientInternalApi
public class ValueClassColumnConverter(
    private val target: KClass<*>,
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

    // Whether SQL NULL should be taken into the value class as an inner null (X(null)). True only
    // when the single underlying constructor parameter is a concrete, nullable type. A generic value
    // class (its underlying is a type parameter, not a KClass) is excluded — its underlying type,
    // and hence nullability, is unknowable. Determined directly from the primary constructor so a
    // genuine reflection/ABI fault surfaces instead of being silently treated as non-nullable, and
    // without resolving `boxer` (which fail-fasts for generic value classes).
    private val nullableUnderlying: Boolean by lazy {
        val underlyingType = target.primaryConstructor?.parameters?.singleOrNull()?.type
        underlyingType != null && underlyingType.classifier is KClass<*> && underlyingType.isMarkedNullable
    }

    /**
     * Converts the [raw] column value (retrieved with no type hint) to the value class.
     * [retrieveTyped] re-reads the same column asking the driver for a given type; it is used to
     * preserve driver coercions (e.g. a numeric column to a `Boolean` underlying) that the
     * ConversionService cannot perform, and must return the raw value if the driver cannot produce
     * the requested type.
     *
     * SQL NULL is taken into the value class as an inner null (X(null)) when the underlying type is
     * nullable, mirroring the write side which binds such a value as SQL NULL; otherwise it maps to
     * a null value class. A nullable property must short-circuit to the outer null before reaching
     * this converter.
     *
     * A registered reading converter wins over automatic boxing whether it is keyed on the raw
     * column type or on the type the driver produces for the underlying retrieval, so the priority
     * is: (1) converter on the raw type, (2) box a raw value already of the underlying type,
     * (3) delegate a nested value class, (4) driver typed retrieval, (5) converter on the typed
     * type, (6) box the typed value (converting through the ConversionService if the driver could
     * not produce the underlying type).
     */
    @Suppress("ReturnCount") // Sequential guard clauses for the retrieval priority read best flat.
    public fun convert(
        raw: Any?,
        retrieveTyped: (Class<*>) -> Any?,
    ): Any? {
        if (raw == null) {
            return if (nullableUnderlying) boxer.boxNull() else null
        }
        // (1) A reading converter keyed on the raw column type takes precedence over boxing.
        if (canConvert(raw.javaClass)) {
            return conversionService.convert(raw, targetJavaType)
        }
        // Resolving `boxer` here (lazily) means a generic value class without a converter fails now,
        // with the descriptive fail-fast message, rather than at construction.
        // (2) The raw value is already the underlying type (a converter on it was ruled out above).
        if (underlyingJavaType.isInstance(raw)) {
            return boxer.box(raw)
        }
        // (3) A nested value class runs the same priority for its own level.
        val nested = underlyingConverter
        if (nested != null) {
            return nested.convert(raw, retrieveTyped)?.let { boxer.box(it) }
        }
        // (4) Ask the driver for the underlying type; it coerces where the ConversionService cannot
        // (e.g. a numeric column to a Boolean underlying). Falls back to the raw value if it cannot.
        val typed = retrieveTyped(underlyingJavaType) ?: raw
        // (5) A reading converter keyed on the type the driver produced (e.g. a String for an enum
        // column) also wins over boxing. When the driver fell back to the raw value, its type was
        // already ruled out in (1), so this is a cached miss.
        if (canConvert(typed.javaClass)) {
            return conversionService.convert(typed, targetJavaType)
        }
        // (6) No converter: box, converting to the underlying type through the ConversionService if
        // the driver could not produce it. A converter for the underlying type may legitimately
        // return null (the Converter contract allows it); map it to null instead of boxing null.
        val underlying = if (underlyingJavaType.isInstance(typed)) {
            typed
        } else {
            conversionService.convert(typed, underlyingJavaType)
        }
        return underlying?.let { boxer.box(it) }
    }

    private fun canConvert(source: Class<*>): Boolean =
        canConvertCache.computeIfAbsent(source) { conversionService.canConvert(it, targetJavaType) }
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
    // registered reading converter targeting the value class is the supported alternative.
    val underlying: KClass<*> = requireNotNull(constructor.parameters.single().type.classifier as? KClass<*>) {
        "Cannot automatically box the generic value class $target: its underlying type is a type parameter. " +
            "Register a reading converter targeting $target instead."
    }

    // Disabled when the underlying is itself a value class: constructor-impl takes the deeply
    // erased type, which would bypass the inner value class's init validation.
    private val fastBox: ValueClasses.FastBox? =
        if (underlying.isValue) null else ValueClasses.fastBoxOrNull(target.java)

    /** Boxes a non-null underlying value while running value class init validation. */
    fun box(value: Any): Any = callConstructor {
        val fast = fastBox
        if (fast != null && fast.accepts(value)) fast.box(value) else constructor.call(value)
    }

    /**
     * Boxes a null underlying value into X(null), running `init` validation. Only called when the
     * underlying type is nullable (see [ValueClassColumnConverter]). Goes through the Kotlin
     * constructor (not the fast path, whose `accepts` is non-null); this runs only on SQL NULL rows,
     * so it is off the hot path.
     */
    fun boxNull(): Any = callConstructor { constructor.call(null) }
}

/**
 * Invokes Kotlin reflection while surfacing constructor validation failures directly, rather
 * than wrapped in [InvocationTargetException].
 */
@KueryClientInternalApi
public fun callConstructor(block: () -> Any?): Any = try {
    checkNotNull(block())
} catch (ex: InvocationTargetException) {
    throw ex.targetException
}
