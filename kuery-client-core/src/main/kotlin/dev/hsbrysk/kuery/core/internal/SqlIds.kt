package dev.hsbrysk.kuery.core.internal

import dev.hsbrysk.kuery.core.SqlBuilder
import java.util.concurrent.ConcurrentHashMap

object SqlIds {
    private val NUMBER_REGEX = "^[0-9]+$".toRegex()

    private val CACHE: ConcurrentHashMap<Class<*>, String> = ConcurrentHashMap()

    internal fun (SqlBuilder.() -> Unit).id(): String {
        return CACHE.computeIfAbsent(this.javaClass) {
            val name = StackWalker.getInstance().walk { frames ->
                frames
                    .filter {
                        it.className != SqlIds::class.java.name
                    }
                    .skip(3)
                    .findFirst()
                    .map { "${it.className}.${it.methodName}" }
                    .orElse(null)
            }
            if (name == null) {
                return@computeIfAbsent "UNKNOWN"
            }

            val parts = name.split("$").filterNot { it.matches(NUMBER_REGEX) }
            if (parts.isEmpty()) {
                "UNKNOWN"
            } else {
                parts.joinToString(".")
            }
        }
    }
}
