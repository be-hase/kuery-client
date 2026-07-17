plugins {
    id("conventions.preset.base")
    id("conventions.maven-publish")
}

description = "Compiler plugin for the Kuery client."

dependencies {
    implementation(kotlin("compiler-embeddable"))
    // Used by the opt-in SQL syntax check. Kept as a plain dependency (not shaded): the compiler
    // plugin classpath is isolated from the user's compile/runtime classpath, so it cannot leak
    // into user code.
    implementation(libs.jsqlparser)
    testImplementation(libs.kotlin.compile.testing)
    testImplementation(projects.kueryClientCore)
}
