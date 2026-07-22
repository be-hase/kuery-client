plugins {
    // Load the Kotlin plugin once so all subprojects share the same plugin classloader.
    alias(libs.plugins.kotlin.jvm) apply false
    id("conventions.kover")
    id("conventions.dokka")
}

dependencies {
    kover(projects.kueryClientCore)
    kover(projects.kueryClientCompiler)
    kover(projects.kueryClientGradlePlugin)
    kover(projects.kueryClientSpringDataCommon)
    kover(projects.kueryClientSpringDataJdbc)
    kover(projects.kueryClientSpringDataR2dbc)

    // Modules aggregated into the Dokka HTML published at https://kuery-client.hsbrysk.dev/api/.
    // Matches the modules that expose a public API (see conventions.public-api usage); the
    // other published modules are internal-only or build tooling.
    dokka(projects.kueryClientCore)
    dokka(projects.kueryClientSpringDataJdbc)
    dokka(projects.kueryClientSpringDataR2dbc)
}

dokka {
    moduleName = "Kuery Client"
}
