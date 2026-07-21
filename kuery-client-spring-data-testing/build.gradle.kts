plugins {
    id("conventions.preset.base")
    // Deliberately no conventions.public-api / conventions.maven-publish: this module is not
    // published, and its contract classes are excluded from explicit API mode and ABI checks.
}

description = "Shared contract tests and fixtures for the spring-data modules. Not published."

dependencies {
    api(projects.kueryClientSpringDataJdbc)
    api(projects.kueryClientSpringDataR2dbc)

    // Contract tests live in the main source set, so test libraries must be declared explicitly
    // (conventions.kotlin only adds them to testImplementation).
    api(platform(libs.junit.bom))
    api("org.junit.jupiter:junit-jupiter-api")
    api(libs.assertk)
    api(libs.kotlin.coroutines.test)

    api(platform(libs.spring.boot.bom))
    api("com.h2database:h2")
    api("io.r2dbc:r2dbc-h2")

    kotlinCompilerPluginClasspath(projects.kueryClientCompiler)
}
