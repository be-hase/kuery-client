package dev.hsbrysk.kuery.compiler.ir.misc

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

object CallableIds {
    val LIST_OF = CallableId(FqName("kotlin.collections"), Name.identifier("listOf"))
}
