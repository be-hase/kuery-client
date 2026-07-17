package dev.hsbrysk.kuery.gradle

import org.gradle.api.provider.Property

interface KueryClientExtension {
    /**
     * When true, the compiler plugin automatically applies `trimIndent()` to every string passed
     * to `SqlBuilder.add` / `String.unaryPlus`, so multi-line SQL no longer needs an explicit
     * `.trimIndent()`. Default: false.
     *
     * See https://kuery-client.hsbrysk.dev/basics#automatic-trimindent-opt-in for the exact
     * semantics (compile-time folding, interaction with explicit trim calls, `addUnsafe`).
     */
    val autoTrimIndent: Property<Boolean>

    /**
     * When true, the compiler plugin validates the SQL syntax of `sql { ... }` blocks at compile
     * time and reports a `KUERY_SQL_SYNTAX` warning on parse failures. Only blocks whose complete
     * statement is statically known are checked (every statement an `add`/`unaryPlus` with a
     * literal/template/const argument); dynamically assembled blocks are skipped. The check uses
     * a generic SQL parser (JSqlParser), so rare vendor-specific syntax may be reported as a
     * false positive — suppress with `@Suppress("KUERY_SQL_SYNTAX")`. Default: false.
     */
    val sqlSyntaxCheck: Property<Boolean>
}
