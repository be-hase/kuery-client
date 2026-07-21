plugins {
    id("conventions.preset.base")
    // Deliberately no conventions.public-api / conventions.maven-publish: this module is not
    // published, and its contract classes are excluded from explicit API mode and ABI checks.
}

description = "Shared contract tests for the spring-data modules. Not published."

dependencies {
    // Depends only on the core interfaces, never on the jdbc/r2dbc implementations, so that one
    // module's test classpath is not polluted with the sibling implementation. Each module keeps
    // its ContractDatabase implementation in its own src/test.
    api(projects.kueryClientCore)
    api(libs.kotlin.coroutines.core)
    api(libs.micrometer.observation)

    // Contract tests live in the main source set, so test libraries must be declared explicitly
    // (conventions.kotlin only adds them to testImplementation).
    api(platform(libs.junit.bom))
    api("org.junit.jupiter:junit-jupiter-api")
    api(libs.assertk)
    api(libs.kotlin.coroutines.test)

    kotlinCompilerPluginClasspath(projects.kueryClientCompiler)
}
