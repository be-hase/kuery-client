package dev.hsbrysk.kuery.compiler.ir.misc

import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.superTypes

internal class StringConcatenationProcessor(private val builder: IrBuilderWithScope) {
    // first: fragments, second: values
    fun process(expressions: List<IrExpression>): Pair<List<IrExpression>, List<IrExpression>> {
        val fragments = mutableListOf<IrExpression>()
        val values = mutableListOf<IrExpression>()

        val iterator = expressions.iterator()
        var mustAddFragment = true

        while (iterator.hasNext()) {
            val current = iterator.next()
            if (isFragment(current)) {
                fragments.add(current)
                mustAddFragment = false
            } else {
                if (mustAddFragment) {
                    fragments.add(builder.irString(""))
                }
                values.add(current)
                mustAddFragment = true
            }
        }

        return fragments to values
    }

    private fun isFragment(expression: IrExpression): Boolean {
        val isString = expression.type.classFqName?.asString() == ClassNames.STRING ||
            expression.type.superTypes().any { it.classFqName?.asString() == ClassNames.STRING }
        return isString && expression is IrConst<*> && expression.kind == IrConstKind.String
    }
}
