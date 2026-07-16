package dev.hsbrysk.kuery.gradle

import org.gradle.api.provider.Property

interface KueryClientExtension {
    /**
     * When true, the compiler plugin automatically applies `trimIndent()` to every string passed
     * to `SqlBuilder.add` / `String.unaryPlus`, so multi-line SQL no longer needs an explicit
     * `.trimIndent()`. For string literals and templates this is computed at compile time and
     * adds no runtime cost. `addUnsafe` is not affected. Default: false.
     */
    val autoTrimIndent: Property<Boolean>
}
