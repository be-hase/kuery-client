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
     * Enables compile-time SQL syntax checking of `sql { ... }` blocks and selects its strictness.
     * Leave unset to disable (the default). Values:
     *
     * - `"generic"` — validate syntax with a generic SQL parser (JSqlParser) and report a
     *   `KUERY_SQL_SYNTAX` warning on parse failures. Fewest false positives.
     * - a dialect name (`ansi`, `oracle`, `mysql`, `sqlserver`, `mariadb`, `postgresql`, `h2`) —
     *   the above, plus a check against that dialect's feature set, reported as a
     *   `KUERY_SQL_DIALECT` warning (e.g. `ON DUPLICATE KEY UPDATE` under `postgresql`).
     *
     * Only blocks whose complete statement is statically known are checked (every statement an
     * `add`/`unaryPlus` with a literal/template/const argument — `trimIndent()`/`trimMargin()`
     * on one included — or a call to a static same-module `SqlBuilder` extension helper, which
     * is inlined); dynamically assembled blocks are skipped.
     * The checks are lenient and may occasionally produce a false positive — suppress with
     * `@Suppress("KUERY_SQL_SYNTAX")` / `@Suppress("KUERY_SQL_DIALECT")`.
     */
    val sqlSyntaxCheck: Property<String>
}
