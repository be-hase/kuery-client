package dev.hsbrysk.kuery.core.internal

import dev.hsbrysk.kuery.core.SqlBuilder2

// TODO: remove

internal class DefaultSqlBuilder2 : SqlBuilder2 {
    private val body = StringBuilder()
    private val parameters = mutableListOf<Any?>()
    private var pluginLoaded = false

    override fun add(sql: String): Unit = injectByPlugin()

    override fun String.unaryPlus(): Unit = injectByPlugin()

    fun addInternal(sql: String) {
        body.appendLine(sql)
    }

    fun interpolate(
        fragments: List<String>,
        values: List<Any?>,
    ): String {
        parameters.addAll(values)
        return fragments.joinToString("?")
    }

    fun build(): Pair<String, List<Any?>> {
        return body.toString().trim() to parameters
    }

    companion object {
        fun <T> injectByPlugin(): T =
            error("kuery-client-compiler plugin is not loaded or you are using an unsupported usage.")
    }
}
