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
}
