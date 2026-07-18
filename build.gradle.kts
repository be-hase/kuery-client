plugins {
    // Load the Kotlin plugin once so all subprojects share the same plugin classloader.
    alias(libs.plugins.kotlin.jvm) apply false
}
