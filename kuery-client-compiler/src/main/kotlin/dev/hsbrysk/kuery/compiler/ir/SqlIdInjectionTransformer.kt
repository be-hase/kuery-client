package dev.hsbrysk.kuery.compiler.ir

import dev.hsbrysk.kuery.compiler.ir.misc.ClassNames
import dev.hsbrysk.kuery.compiler.ir.misc.SqlIdComputer
import dev.hsbrysk.kuery.compiler.misc.CallableIds
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionReference
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.parentClassOrNull

/**
 * Rewrites calls to the sqlId-less overloads `KueryClient.sql(block)` /
 * `KueryBlockingClient.sql(block)` into `sql(sqlIdProvidingBlock("<id>", block))`, deriving the
 * id from the enclosing declaration at compile time (see [SqlIdComputer]).
 *
 * Only calls whose block argument is written literally at the call site (a lambda or a
 * function reference) are rewritten. This keeps every other expression intact — most notably
 * mocking-library argument matchers (`every { client.sql(any()) }` must see the matcher
 * placeholder unwrapped) — and it means pass-through code such as decorators and
 * compiler-generated interface-delegation bridges (`override fun sql(block) = d.sql(block)`)
 * never re-wraps a block, so the original call site's id survives the delegation. A block
 * passed as a variable resolves to the id "NONE" at runtime.
 *
 * When one declaration contains several rewritten calls, their ids are disambiguated with a
 * `#N` suffix in source order (`com.example.UserRepository.find#1`, `...#2`); a declaration
 * with a single call keeps the plain id. The counters are keyed by the enclosing declaration
 * itself, not by the id string, so declarations whose ids collide (e.g. overloads) keep their
 * plain ids. The bookkeeping is scoped per file so that ids stay stable under incremental
 * compilation, which recompiles whole files.
 */
@Suppress("OPT_IN_USAGE")
class SqlIdInjectionTransformer(
    private val pluginContext: IrPluginContext,
    private val messageCollector: MessageCollector,
) : IrElementTransformerVoidWithContext() {
    // null when the resolution failed: the core on the classpath is an older version without
    // the factory, so skip injection entirely (the runtime then uses the id "NONE") instead of
    // generating calls that cannot link. A warning is emitted when a call that would have been
    // rewritten is encountered (see visitFileNew).
    private val factory by lazy(LazyThreadSafetyMode.NONE) {
        pluginContext.finderForBuiltins().findFunctions(CallableIds.SQL_ID_PROVIDING_BLOCK).singleOrNull()
    }

    private var warnedAboutStaleCore = false

    // Total rewritten calls per anchor declaration in the current file (counting pass), and how
    // many have been numbered so far (transform pass).
    private var counts: Map<IrDeclaration, Int> = emptyMap()
    private val counters = mutableMapOf<IrDeclaration, Int>()

    override fun visitFileNew(declaration: IrFile): IrFile {
        if (factory == null) {
            if (!warnedAboutStaleCore && countTargets(declaration).isNotEmpty()) {
                warnedAboutStaleCore = true
                messageCollector.report(
                    CompilerMessageSeverity.WARNING,
                    "sqlId injection is disabled: ${CallableIds.SQL_ID_PROVIDING_BLOCK.asSingleFqName()} was not " +
                        "found on the classpath. The kuery-client-core version is likely older than the compiler " +
                        "plugin; align the two, or every auto-generated sqlId resolves to \"NONE\" at runtime.",
                )
            }
            return declaration
        }
        counts = countTargets(declaration)
        counters.clear()
        return super.visitFileNew(declaration)
    }

    private fun countTargets(declaration: IrFile): Map<IrDeclaration, Int> =
        CountingPass().also { declaration.transformChildren(it, null) }.counts

    override fun visitCall(expression: IrCall): IrExpression {
        val factory = factory
        if (factory == null || !expression.isInjectionTarget()) {
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
        val anchor = SqlIdComputer.anchor(scopeOwnerDeclaration())
        val base = SqlIdComputer.baseId(anchor)
        return if ((counts[anchor] ?: 0) > 1) {
            "$base#${counters.merge(anchor, 1, Int::plus)}"
        } else {
            base
        }
    }

    private fun scopeOwnerDeclaration(): IrDeclaration =
        checkNotNull(currentScope).scope.scopeOwnerSymbol.owner as IrDeclaration

    // Tallies the rewritten calls per anchor declaration, traversing exactly like the transform
    // pass so that both passes resolve identical enclosing scopes and target sets.
    private inner class CountingPass : IrElementTransformerVoidWithContext() {
        val counts = mutableMapOf<IrDeclaration, Int>()

        override fun visitCall(expression: IrCall): IrExpression {
            if (expression.isInjectionTarget()) {
                val scopeOwner = checkNotNull(currentScope).scope.scopeOwnerSymbol.owner as IrDeclaration
                counts.merge(SqlIdComputer.anchor(scopeOwner), 1, Int::plus)
            }
            return super.visitCall(expression)
        }
    }

    companion object {
        private val SQL_CLIENT_CLASS_NAMES = setOf(ClassNames.KUERY_CLIENT, ClassNames.KUERY_BLOCKING_CLIENT)

        private fun IrCall.isInjectionTarget(): Boolean = isSqlWithoutId() && blockArgumentOrNull().isBlockLiteral()

        private fun IrCall.blockArgumentOrNull(): IrExpression? {
            val blockParameter = symbol.owner.parameters.first { it.kind == IrParameterKind.Regular }
            return arguments[blockParameter.indexInParameters]
        }

        private fun IrExpression?.isBlockLiteral(): Boolean =
            this is IrFunctionExpression || this is IrFunctionReference

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
