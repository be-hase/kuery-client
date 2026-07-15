package dev.hsbrysk.kuery.core.internal

import dev.hsbrysk.kuery.core.KueryClientInternalApi
import dev.hsbrysk.kuery.core.SqlBuilder

@KueryClientInternalApi
public object SqlIds {
    private val NUMBER_REGEX = "^[0-9]+$".toRegex()

    // ClassValue stores the computed id on the block's Class itself instead of referencing
    // the Class from a static map, so the Class and its ClassLoader remain garbage collectable
    // (e.g. with hot-reload class loaders such as Spring DevTools).
    private val CACHE = object : ClassValue<String>() {
        override fun computeValue(type: Class<*>): String = callerId()
    }

    private val SUFFIXES = listOf(
        ".invokeSuspend",
        "${'$'}suspendImpl",
        // for lambdas compiled as classes (e.g. -Xlambdas=class)
        ".invoke",
    )

    // Lambdas are compiled via invokedynamic into synthetic methods named `foo$lambda$0`
    // (nested lambdas repeat the pattern).
    private val LAMBDA_METHOD_SUFFIX_REGEX = "(\\\$lambda\\\$[0-9]+)+$".toRegex()

    /**
     * Uses StackWalker to retrieve the caller.
     *
     * The id is computed once per block class from the call site of the first invocation and
     * then cached. Therefore, if the same block (e.g. one stored in a property) is passed from
     * multiple call sites, all of them observe the id of whichever call site ran first.
     * Define blocks inline if each call site should get its own id.
     */
    public fun (SqlBuilder.() -> Unit).id(): String = CACHE.get(this.javaClass)

    private fun callerId(): String {
        val name = StackWalker.getInstance().walk { frames ->
            frames
                .filter {
                    !it.className.startsWith("java.lang.ClassValue")
                }
                .filter {
                    !it.className.startsWith("dev.hsbrysk.kuery")
                }
                .findFirst()
                .map { "${it.className}.${it.methodName}" }
                .orElse(null)
        }
        if (name == null) {
            return "UNKNOWN"
        }

        val parts = name
            .replace(LAMBDA_METHOD_SUFFIX_REGEX, "")
            .removeSuffixes(SUFFIXES)
            .split("$", ".")
            .filterNot { it.matches(NUMBER_REGEX) }
        return if (parts.isEmpty()) {
            "UNKNOWN"
        } else {
            parts.joinToString(".")
        }
    }

    internal fun String.removeSuffixes(suffixes: List<String>): String {
        suffixes.forEach { suffix ->
            if (this.endsWith(suffix)) {
                return this.removeSuffix(suffix)
            }
        }
        return this
    }
}
