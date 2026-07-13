package dev.hsbrysk.kuery.compiler.ir.misc

import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.expressions.IrExpression

internal class StringConcatenationProcessor(private val builder: IrBuilderWithScope) {
    // first: fragments, second: values
    fun process(expressions: List<IrExpression>): Pair<List<IrExpression>, List<IrExpression>> {
        val fragments = mutableListOf<IrExpression>()
        val values = mutableListOf<IrExpression>()

        // K2 constant-folds `const val` references into standalone IrConst nodes without merging
        // them with adjacent literal fragments, so consecutive constants must be concatenated here
        // to keep the fragments/values invariant expected by `DefaultSqlBuilder.interpolate`.
        val pending = StringBuilder()
        for (expression in expressions) {
            val text = fragmentTextOrNull(expression)
            if (text != null) {
                pending.append(text)
            } else {
                // The reason for adding an empty string fragment can be found in `DefaultSqlBuilder.interpolate`.
                fragments.add(builder.irString(pending.toString()))
                pending.clear()
                values.add(expression)
            }
        }
        if (pending.isNotEmpty()) {
            fragments.add(builder.irString(pending.toString()))
        }

        return fragments to values
    }

    // Compile-time String/Char constants become SQL text, while runtime values are bound as parameters.
    private fun fragmentTextOrNull(expression: IrExpression): String? {
        if (expression !is IrConst) {
            return null
        }
        return when (expression.kind) {
            IrConstKind.String, IrConstKind.Char -> expression.value.toString()
            else -> null
        }
    }
}
