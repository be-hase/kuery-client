package dev.hsbrysk.kuery.compiler.ir.misc

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal object ClassIds {
    val DEFAULT_SQL_BUILDER = ClassId(
        FqName("dev.hsbrysk.kuery.core.internal"),
        Name.identifier("DefaultSqlBuilder2"),
    )
}
