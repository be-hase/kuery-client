package dev.hsbrysk.kuery.spring.jdbc.internal

import org.springframework.util.ClassUtils
import java.lang.reflect.Method

// NOTE: This object is intentionally duplicated in the r2dbc module
// (dev.hsbrysk.kuery.spring.r2dbc.internal.ValueClasses); there is no shared Spring-dependent
// module to host it. Keep both copies in sync.
/**
 * Reflection helpers for Kotlin value classes (`@JvmInline`), used to transparently bind a value
 * class as its underlying value. Uses plain Java reflection (`@JvmInline` is runtime-visible and
 * the compiler-generated `unbox-impl` method exposes the underlying value), which keeps the
 * per-parameter bind hot path free of kotlin-reflect machinery.
 */
internal object ValueClasses {
    // ClassValue (instead of a Class-keyed map) so cached entries are tied to the class itself
    // and never pin user classloaders (e.g. across Spring DevTools restarts).
    private val unboxMethods = object : ClassValue<Method>() {
        override fun computeValue(type: Class<*>): Method = type.getDeclaredMethod("unbox-impl").apply {
            // The method is public, but the declaring class may not be (e.g. a file-private
            // value class compiles to a package-private JVM class).
            isAccessible = true
        }
    }

    /**
     * Returns the underlying value of a boxed value class instance. May be null when the
     * underlying type is nullable (e.g. `value class Opt(val value: String?)`).
     */
    fun unbox(value: Any): Any? = unboxMethods.get(value.javaClass).invoke(value)

    /**
     * The JVM type of the underlying value, e.g. `UserName` -> `String`. Primitives are boxed
     * (`Int` -> `java.lang.Integer`) because callers use this as an object array component type.
     */
    fun underlyingType(clazz: Class<*>): Class<*> =
        ClassUtils.resolvePrimitiveIfNecessary(unboxMethods.get(clazz).returnType)
}
