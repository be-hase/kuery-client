package dev.hsbrysk.kuery.core.internal

import dev.hsbrysk.kuery.core.SqlBuilder2

// TODO: remove

internal class DefaultSqlBuilder2 : SqlBuilder2 {
    private val body = StringBuilder()
    private val parameters = mutableListOf<Any?>()

    override fun add(sql: String) {
        body.appendLine(sql)
    }

    override fun String.unaryPlus() {
        add(this)
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
}
