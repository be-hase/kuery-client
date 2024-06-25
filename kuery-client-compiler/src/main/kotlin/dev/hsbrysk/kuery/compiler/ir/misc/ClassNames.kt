package dev.hsbrysk.kuery.compiler.ir.misc

import dev.hsbrysk.kuery.core.SqlBuilder2

internal object ClassNames {
    val STRING = checkNotNull(String::class.qualifiedName)
    val SQL_BUILDER = checkNotNull(SqlBuilder2::class.qualifiedName)
}
