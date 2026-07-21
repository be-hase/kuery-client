package dev.hsbrysk.kuery.spring.data.internal

import dev.hsbrysk.kuery.core.KueryClientInternalApi
import org.springframework.util.ClassUtils
import java.lang.reflect.Method

/**
 * Reflection over the compiler-generated JVM ABI of Kotlin value classes (`@JvmInline`). This is
 * the single owner of the mangled member names — `unbox-impl` (used on the write path to bind a
 * value class as its underlying value) and the `constructor-impl` / `box-impl` pair (used on the
 * read path to box an underlying value back into a value class). Plain Java reflection keeps the
 * per-parameter bind hot path free of kotlin-reflect machinery.
 */
@KueryClientInternalApi
public object ValueClasses {
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
    public fun unbox(value: Any): Any? = unboxMethods.get(value.javaClass).invoke(value)

    /**
     * The JVM type of the underlying value, e.g. `UserName` -> `String`. Primitives are boxed
     * (`Int` -> `java.lang.Integer`) because callers use this as an object array component type.
     */
    public fun underlyingType(clazz: Class<*>): Class<*> =
        ClassUtils.resolvePrimitiveIfNecessary(unboxMethods.get(clazz).returnType)

    /**
     * The `constructor-impl` (which contains the `init` validation) + `box-impl` pair for boxing
     * an underlying value into [valueClass] through plain Java reflection. Null when the class
     * does not expose exactly one of each (e.g. a value class with a secondary constructor), in
     * which case callers fall back to the kotlin-reflect constructor.
     */
    internal fun fastBoxOrNull(valueClass: Class<*>): FastBox? {
        val constructorImpl = valueClass.singleDeclaredMethodOrNull("constructor-impl") ?: return null
        val boxImpl = valueClass.singleDeclaredMethodOrNull("box-impl") ?: return null
        return FastBox(constructorImpl, boxImpl)
    }

    private fun Class<*>.singleDeclaredMethodOrNull(name: String): Method? =
        declaredMethods.singleOrNull { it.name == name }?.apply { isAccessible = true }

    /** Boxes an underlying value into a value class via its static `constructor-impl`/`box-impl`. */
    internal class FastBox(
        private val constructorImpl: Method,
        private val boxImpl: Method,
    ) {
        private val parameterType: Class<*> = constructorImpl.parameterTypes.single()

        /** Whether [value] matches the (erased) underlying type this fast path expects. */
        fun accepts(value: Any): Boolean = ClassUtils.resolvePrimitiveIfNecessary(parameterType).isInstance(value)

        // constructor-impl runs `init`; box-impl wraps the result. May throw
        // InvocationTargetException, which the caller unwraps.
        fun box(value: Any): Any? = boxImpl.invoke(null, constructorImpl.invoke(null, value))
    }
}
