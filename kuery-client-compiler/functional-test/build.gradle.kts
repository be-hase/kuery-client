plugins {
    id("conventions.preset.base")
    // id("dev.hsbrysk.kuery-client") version "0.4.0-SNAPSHOT"
}

description = "Kuery client's compiler functional test module."

dependencies {
    implementation(projects.kueryClientCore)
    kotlinCompilerPluginClasspath(projects.kueryClientCompiler)
}
