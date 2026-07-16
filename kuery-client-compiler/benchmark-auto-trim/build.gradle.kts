plugins {
    id("conventions.preset.base")
}

description = "Query fixtures compiled with autoTrimIndent enabled, for the compiler benchmark module."

dependencies {
    implementation(projects.kueryClientCore)
    kotlinCompilerPluginClasspath(projects.kueryClientCompiler)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-P", "plugin:dev.hsbrysk.kuery-client:autoTrimIndent=true")
    }
}
