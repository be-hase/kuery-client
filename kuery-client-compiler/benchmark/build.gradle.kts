plugins {
    id("conventions.preset.base")
    id("conventions.jmh")
}

description = "JMH benchmarks for the compiler plugin's SQL building output."

dependencies {
    implementation(projects.kueryClientCore)
    implementation(projects.kueryClientCompiler.benchmarkAutoTrim)
    kotlinCompilerPluginClasspath(projects.kueryClientCompiler)
}
