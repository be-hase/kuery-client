package dev.hsbrysk.kuery.core.internal

import dev.hsbrysk.kuery.core.KueryClientInternalApi
import dev.hsbrysk.kuery.core.SqlBuilder

@KueryClientInternalApi
public object SqlIds {
    /**
     * Returns the sqlId that the compiler plugin attached to this block at the call site, or
     * `"NONE"` if the call site was not compiled with the compiler plugin (e.g. Java callers
     * or reflective invocations).
     */
    public fun (SqlBuilder.() -> Unit).id(): String = if (this is SqlIdProvidingBlock) sqlId else "NONE"
}

/**
 * Wraps a `sql { ... }` block together with the sqlId derived from its call site.
 *
 * Only referenced by code the compiler plugin generates: calls to the sqlId-less
 * `KueryClient.sql(block)` / `KueryBlockingClient.sql(block)` overloads are rewritten into
 * `sql(sqlIdProvidingBlock("<enclosing declaration>", block))` at compile time.
 */
@KueryClientInternalApi
public fun sqlIdProvidingBlock(
    sqlId: String,
    block: SqlBuilder.() -> Unit,
): SqlBuilder.() -> Unit = SqlIdProvidingBlock(sqlId, block)

// An extension function type is not allowed as a supertype, so implement the plain function
// type instead; non-literal values of the two are interchangeable.
internal class SqlIdProvidingBlock(
    @JvmField val sqlId: String,
    private val delegate: SqlBuilder.() -> Unit,
) : (SqlBuilder) -> Unit {
    override fun invoke(p1: SqlBuilder) {
        delegate(p1)
    }
}
