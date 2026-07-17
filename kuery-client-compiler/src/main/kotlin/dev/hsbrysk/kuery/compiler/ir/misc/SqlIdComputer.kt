package dev.hsbrysk.kuery.compiler.ir.misc

import org.jetbrains.kotlin.ir.declarations.IrAnonymousInitializer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.util.fileOrNull

/**
 * Derives the sqlId base (without the disambiguating `#N` suffix) for a `sql {}` call site from
 * its enclosing declarations: the Kotlin FQN of the innermost named declaration, e.g.
 * `com.example.UserRepository.findById`.
 *
 * - lambdas, anonymous functions, and anonymous objects carry no name and contribute no
 *   segment of their own (named members of an anonymous object still do)
 * - property accessors and property initializers use the property name
 * - constructors and init blocks use `<init>`
 * - local functions contribute their name after the enclosing function's
 *   (`com.example.Outer.outer.local`)
 * - top-level functions use `package.function` (the Kotlin FQN, not the JVM facade class name)
 */
@Suppress("OPT_IN_USAGE")
internal object SqlIdComputer {
    /**
     * The innermost enclosing declaration that contributes a name segment — what a call site's
     * id is anchored to. `#N` numbering is scoped to this declaration (not to the id string),
     * so declarations whose ids happen to collide (e.g. overloads) never affect each other's
     * numbering.
     */
    fun anchor(innermost: IrDeclaration): IrDeclaration = generateSequence(innermost) { it.parent as? IrDeclaration }
        .firstOrNull { segmentOrNull(it) != null }
        ?: innermost

    fun baseId(innermost: IrDeclaration): String {
        val segments = generateSequence(innermost) { it.parent as? IrDeclaration }
            .mapNotNull { segmentOrNull(it) }
            .toList()
            .asReversed()
        val packageName = innermost.fileOrNull?.packageFqName?.takeUnless { it.isRoot }?.asString()
        return (listOfNotNull(packageName) + segments).joinToString(".")
    }

    private fun segmentOrNull(declaration: IrDeclaration): String? = when (declaration) {
        is IrSimpleFunction -> when {
            // Check the accessor link before the name: accessor names are special (`<get-foo>`).
            declaration.correspondingPropertySymbol != null ->
                declaration.correspondingPropertySymbol?.owner?.name?.asString()
            declaration.name.isSpecial -> null
            else -> declaration.name.asString()
        }
        is IrConstructor, is IrAnonymousInitializer -> "<init>"
        is IrField -> declaration.correspondingPropertySymbol?.owner?.name?.asString() ?: declaration.name.asString()
        is IrProperty -> declaration.name.asString()
        is IrClass -> if (declaration.name.isSpecial) null else declaration.name.asString()
        else -> null
    }
}
