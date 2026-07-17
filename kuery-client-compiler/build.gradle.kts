plugins {
    id("conventions.preset.base")
    id("conventions.maven-publish")
}

description = "Compiler plugin for the Kuery client."

dependencies {
    implementation(kotlin("compiler-embeddable"))
    testImplementation(libs.kotlin.compile.testing)
    testImplementation(projects.kueryClientCore)
    // core exposes coroutines as compileOnly; the recording clients and the compiled snippets
    // (e.g. runBlocking) need it on the test classpath.
    testImplementation(libs.kotlin.coroutines.core)
}
