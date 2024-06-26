package dev.hsbrysk.kuery.compiler.ir.misc

import dev.hsbrysk.kuery.core.SqlBuilder

internal object ClassNames {
    val STRING = checkNotNull(String::class.qualifiedName)
    val SQL_BUILDER = checkNotNull(SqlBuilder::class.qualifiedName)
}
