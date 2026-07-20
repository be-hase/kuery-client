plugins {
    id("conventions.preset.base")
    id("conventions.public-api")
    id("conventions.maven-publish")
    id("conventions.jmh")
}

description = "Kuery client implementation using spring-data-r2dbc."

dependencies {
    api(projects.kueryClientCore)

    api(libs.spring.data.r2dbc)
    api(libs.kotlin.coroutines.core)
    api(libs.kotlin.coroutines.reactor)

    // Value class mapping (boxing through the primary constructor) needs full Kotlin reflection.
    implementation(kotlin("reflect"))

    // Required only to compile against ObservationThreadLocalAccessor.KEY (a compile-time
    // constant inlined into the bytecode), so it is not needed at runtime.
    compileOnly(libs.micrometer.context.propagation)

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation("com.mysql:mysql-connector-j")
    testImplementation("io.asyncer:r2dbc-mysql")
    testImplementation("io.r2dbc:r2dbc-h2")
    testImplementation("org.postgresql:r2dbc-postgresql")
    testImplementation("org.testcontainers:testcontainers-mysql")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation(libs.kotlin.coroutines.test)
    testImplementation(libs.micrometer.context.propagation)
    testImplementation(libs.micrometer.observation.test)

    kotlinCompilerPluginClasspath(projects.kueryClientCompiler)
}
