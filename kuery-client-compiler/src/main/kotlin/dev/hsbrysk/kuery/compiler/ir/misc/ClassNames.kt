package dev.hsbrysk.kuery.compiler.ir.misc

import dev.hsbrysk.kuery.compiler.misc.ClassIds

// String forms for the IR-side fqName comparisons, derived from the shared ClassIds.
internal object ClassNames {
    val SQL_BUILDER: String = ClassIds.SQL_BUILDER.asFqNameString()
    val KUERY_CLIENT: String = ClassIds.KUERY_CLIENT.asFqNameString()
    val KUERY_BLOCKING_CLIENT: String = ClassIds.KUERY_BLOCKING_CLIENT.asFqNameString()
}
