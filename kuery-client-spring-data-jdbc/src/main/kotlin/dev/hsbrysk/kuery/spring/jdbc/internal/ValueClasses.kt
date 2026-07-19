package dev.hsbrysk.kuery.spring.jdbc.internal

import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

// NOTE: This object is intentionally duplicated in the r2dbc module
// (dev.hsbrysk.kuery.spring.r2dbc.internal.ValueClasses); there is no shared Spring-dependent
// module to host it. Keep both copies in sync.
/**
 * Reflection helpers for Kotlin value classes (`@JvmInline`), used to transparently bind a value
 * class as its underlying value. Works without kotlin-reflect: `@JvmInline` is runtime-visible
 * and the compiler-generated `unbox-impl` method exposes the underlying value.
 */
internal object ValueClasses {
    private val unboxMethodCache = ConcurrentHashMap<Class<*>, Method>()

    fun isValueClass(clazz: Class<*>): Boolean = clazz.isAnnotationPresent(JvmInline::class.java)

    /**
     * Returns the underlying value of a boxed value class instance. May be null when the
     * underlying type is nullable (e.g. `value class Opt(val value: String?)`).
     */
    fun unbox(value: Any): Any? = unboxMethod(value.javaClass).invoke(value)

    /**
     * The JVM type of the underlying value, e.g. `UserName` -> `String`. Primitives are boxed
     * (`Int` -> `java.lang.Integer`) because callers use this as an object array component type.
     */
    fun underlyingType(clazz: Class<*>): Class<*> = unboxMethod(clazz).returnType.boxed()

    private fun unboxMethod(clazz: Class<*>): Method = unboxMethodCache.computeIfAbsent(clazz) {
        it.getDeclaredMethod("unbox-impl")
    }

    private val primitiveToBoxed: Map<Class<*>, Class<*>> = listOf(
        Boolean::class,
        Byte::class,
        Short::class,
        Char::class,
        Int::class,
        Long::class,
        Float::class,
        Double::class,
    ).associate { it.javaPrimitiveType!! to it.javaObjectType }

    private fun Class<*>.boxed(): Class<*> = primitiveToBoxed[this] ?: this
}
