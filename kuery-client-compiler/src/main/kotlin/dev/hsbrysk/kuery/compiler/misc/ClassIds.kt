package dev.hsbrysk.kuery.compiler.misc

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * The single definition of the kuery-client-core classes this plugin recognizes, shared by the
 * FIR checkers and the IR transformers so the two sides cannot recognize different classes.
 */
internal object ClassIds {
    val CORE_PACKAGE = FqName("dev.hsbrysk.kuery.core")

    val SQL_BUILDER = ClassId(CORE_PACKAGE, Name.identifier("SqlBuilder"))
    val DEFAULT_SQL_BUILDER = ClassId(CORE_PACKAGE, Name.identifier("DefaultSqlBuilder"))
    val KUERY_CLIENT = ClassId(CORE_PACKAGE, Name.identifier("KueryClient"))
    val KUERY_BLOCKING_CLIENT = ClassId(CORE_PACKAGE, Name.identifier("KueryBlockingClient"))

    /** The client interfaces whose `sql([sqlId,] block)` members start a SQL builder block. */
    val SQL_CLIENTS = setOf(KUERY_CLIENT, KUERY_BLOCKING_CLIENT)
}
