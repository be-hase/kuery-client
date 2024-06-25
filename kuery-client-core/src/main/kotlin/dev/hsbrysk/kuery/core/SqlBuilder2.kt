package dev.hsbrysk.kuery.core

import dev.hsbrysk.kuery.core.internal.DefaultSqlBuilder2
import org.intellij.lang.annotations.Language

// TODO: remove

@SqlBuilderMarker
interface SqlBuilder2 {
    /**
     * Specify the sql you want to execute. Appended to the internally held [StringBuilder].
     */
    fun add(@Language("sql") sql: String)

    /**
     * Specify the sql you want to execute. Appended to the internally held [StringBuilder].
     */
    operator fun String.unaryPlus()
}

fun sql2(block: SqlBuilder2.() -> Unit): Pair<String, List<Any?>> {
    val builder = DefaultSqlBuilder2()
    block(builder)
    return builder.build()
}
