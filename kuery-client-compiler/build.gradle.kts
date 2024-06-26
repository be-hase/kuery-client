plugins {
    id("conventions.preset.base")
    id("conventions.maven-publish")
}

description = "Compiler plugin for the Kuery client."

dependencies {
    implementation(projects.kueryClientCore)
    implementation(kotlin("compiler-embeddable"))
    testImplementation(libs.kotlin.compile.testing)
}
