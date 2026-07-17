package dev.hsbrysk.kuery.compiler.ir

import dev.hsbrysk.kuery.compiler.ir.misc.ClassNames
import dev.hsbrysk.kuery.compiler.ir.misc.SqlIdComputer
import dev.hsbrysk.kuery.compiler.misc.CallableIds
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.ScopeWithIr
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.parentClassOrNull

/**
 * Rewrites calls to the sqlId-less overloads `KueryClient.sql(block)` /
 * `KueryBlockingClient.sql(block)` into `sql(sqlIdProvidingBlock("<id>", block))`, deriving the
 * id from the enclosing declaration at compile time (see [SqlIdComputer]).
 *
 * When one declaration contains several such calls, their ids are disambiguated with a `#N`
 * suffix in source order (`com.example.UserRepository.find#1`, `...#2`); a declaration with a
 * single call keeps the plain id. The suffix bookkeeping is scoped per file so that ids stay
 * stable under incremental compilation, which recompiles whole files.
 */
@Suppress("OPT_IN_USAGE")
class SqlIdInjectionTransformer(private val pluginContext: IrPluginContext) : IrElementTransformerVoidWithContext() {
    // null when the resolution failed: the core on the classpath is an older version without
    // the factory, so skip injection entirely (the runtime then uses the id "NONE") instead of
    // generating calls that cannot link.
    private val factory by lazy(LazyThreadSafetyMode.NONE) {
        pluginContext.finderForBuiltins().findFunctions(CallableIds.SQL_ID_PROVIDING_BLOCK).singleOrNull()
    }

    // Total sql(block) calls per base id in the current file (counting pass), and how many have
    // been numbered so far (transform pass).
    private var counts: Map<String, Int> = emptyMap()
    private val counters = mutableMapOf<String, Int>()

    override fun visitFileNew(declaration: IrFile): IrFile {
        if (factory == null) {
            return declaration
        }
        counts = CountingPass().also { declaration.transformChildren(it, null) }.counts
        counters.clear()
        return super.visitFileNew(declaration)
    }

    override fun visitCall(expression: IrCall): IrExpression {
        val factory = factory
        if (factory == null || !expression.isSqlWithoutId() || isDelegatedMember(currentFunction)) {
            return super.visitCall(expression)
        }

        // Reserve the id before transforming children so that `#N` numbering follows source
        // order even for (pathological) sql calls nested inside another sql call's block.
        val sqlId = nextSqlId()
        val call = super.visitCall(expression) as IrCall

        val blockParameter = call.symbol.owner.parameters.first { it.kind == IrParameterKind.Regular }
        val blockArgument = checkNotNull(call.arguments[blockParameter.indexInParameters])
        val builder = DeclarationIrBuilder(
            pluginContext,
            checkNotNull(currentScope).scope.scopeOwnerSymbol,
            call.startOffset,
            call.endOffset,
        )
        call.arguments[blockParameter.indexInParameters] = builder.irCall(factory).apply {
            val parameters = factory.owner.parameters.filter { it.kind == IrParameterKind.Regular }
            arguments[parameters[0]] = builder.irString(sqlId)
            arguments[parameters[1]] = blockArgument
        }
        return call
    }

    private fun nextSqlId(): String {
        val base = baseId()
        return if ((counts[base] ?: 0) > 1) {
            "$base#${counters.merge(base, 1, Int::plus)}"
        } else {
            base
        }
    }

    private fun baseId(): String =
        SqlIdComputer.baseId(checkNotNull(currentScope).scope.scopeOwnerSymbol.owner as IrDeclaration)

    // Tallies the sql(block) calls per base id, traversing exactly like the transform pass so
    // that both passes resolve identical enclosing scopes.
    private inner class CountingPass : IrElementTransformerVoidWithContext() {
        val counts = mutableMapOf<String, Int>()

        override fun visitCall(expression: IrCall): IrExpression {
            if (expression.isSqlWithoutId() && !isDelegatedMember(currentFunction)) {
                val base = SqlIdComputer.baseId(
                    checkNotNull(currentScope).scope.scopeOwnerSymbol.owner as IrDeclaration,
                )
                counts.merge(base, 1, Int::plus)
            }
            return super.visitCall(expression)
        }
    }

    companion object {
        private val SQL_CLIENT_CLASS_NAMES = setOf(ClassNames.KUERY_CLIENT, ClassNames.KUERY_BLOCKING_CLIENT)

        // Interface delegation (`class Foo(d: KueryClient) : KueryClient by d`) generates a
        // bridge `override fun sql(block) = d.sql(block)`. Rewriting the call inside the bridge
        // would re-wrap the block and shadow the caller's id with the bridge's, so let the
        // already-wrapped block flow through untouched instead.
        private fun isDelegatedMember(scope: ScopeWithIr?): Boolean =
            (scope?.irElement as? IrDeclaration)?.origin == IrDeclarationOrigin.DELEGATED_MEMBER

        // Identify the call by the resolved declaration's origin. The receiver's static type may
        // be a subtype (a decorator or an application-defined `interface MyClient : KueryClient`),
        // in which case the symbol points at a fake override or an actual override — walk the
        // overridden chain up to the declaring interface.
        private fun IrCall.isSqlWithoutId(): Boolean {
            val function = symbol.owner
            if (function.name.asString() != "sql") return false
            if (function.parameters.count { it.kind == IrParameterKind.Regular } != 1) return false
            return function.isDeclaredInKueryClient()
        }

        private fun IrSimpleFunction.isDeclaredInKueryClient(): Boolean {
            if (parentClassOrNull?.fqNameWhenAvailable?.asString() in SQL_CLIENT_CLASS_NAMES) return true
            return overriddenSymbols.any { it.owner.isDeclaredInKueryClient() }
        }
    }
}
