plugins {
    id("conventions.kotlin")
    id("conventions.ktlint")
    id("conventions.detekt")
    id("conventions.maven-publish")
}

description = "Kuery client's compiler module."

dependencies {
    implementation(projects.kueryClientCore)
    implementation(kotlin("compiler-embeddable"))
    testImplementation(libs.kotlin.compile.testing)
}
