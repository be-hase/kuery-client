plugins {
    // Load the Kotlin plugin once so all subprojects share the same plugin classloader.
    alias(libs.plugins.kotlin.jvm) apply false
    id("conventions.kover")
}

dependencies {
    kover(projects.kueryClientCore)
    kover(projects.kueryClientCompiler)
    kover(projects.kueryClientSpringDataCommon)
    kover(projects.kueryClientSpringDataJdbc)
    kover(projects.kueryClientSpringDataR2dbc)
    kover(projects.kueryClientSpringDataTesting)
}
