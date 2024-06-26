plugins {
    id("conventions.kotlin")
    id("conventions.ktlint")
    id("conventions.detekt")
    id("conventions.maven-publish")
}

description = "Compiler plugin for the Kuery client."

dependencies {
    implementation(projects.kueryClientCore)
    implementation(kotlin("compiler-embeddable"))
    testImplementation(libs.kotlin.compile.testing)
}
