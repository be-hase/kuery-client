plugins {
    id("conventions.kotlin")
    id("conventions.ktlint")
    id("conventions.detekt")
}

description = "Kuery client's compiler functional test module."

dependencies {
    implementation(projects.kueryClientCore)
    kotlinCompilerPluginClasspath(projects.kueryClientCompiler)
}
