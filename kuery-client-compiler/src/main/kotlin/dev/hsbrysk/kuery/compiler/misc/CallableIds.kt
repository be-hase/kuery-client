package dev.hsbrysk.kuery.compiler.misc

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal object CallableIds {
    val LIST_OF = CallableId(FqName("kotlin.collections"), Name.identifier("listOf"))
    val TRIM_INDENT = CallableId(FqName("kotlin.text"), Name.identifier("trimIndent"))
    val TRIM_MARGIN = CallableId(FqName("kotlin.text"), Name.identifier("trimMargin"))
}
