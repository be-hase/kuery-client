plugins {
    id("conventions.preset.base")
    id("conventions.maven-publish")
    id("conventions.jmh")
}

description = "Kuery client implementation using spring-data-r2dbc."

dependencies {
    api(projects.kueryClientCore)

    api(libs.spring.data.r2dbc)
    api(libs.kotlin.coroutines.core)
    api(libs.kotlin.coroutines.reactor)

    // Required only to compile against ObservationThreadLocalAccessor.KEY (a compile-time
    // constant inlined into the bytecode), so it is not needed at runtime.
    compileOnly(libs.micrometer.context.propagation)

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation("com.mysql:mysql-connector-j")
    testImplementation("io.asyncer:r2dbc-mysql")
    testImplementation("org.testcontainers:testcontainers-mysql")
    testImplementation(libs.kotlin.coroutines.test)
    testImplementation(libs.micrometer.context.propagation)
    testImplementation(libs.micrometer.observation.test)

    kotlinCompilerPluginClasspath(projects.kueryClientCompiler)
}
