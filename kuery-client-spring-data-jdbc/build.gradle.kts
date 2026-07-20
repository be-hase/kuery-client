plugins {
    id("conventions.preset.base")
    id("conventions.public-api")
    id("conventions.maven-publish")
    id("conventions.jmh")
}

description = "Kuery client implementation using spring-data-jdbc."

dependencies {
    api(projects.kueryClientCore)

    api(libs.spring.data.jdbc)

    // Value class mapping (boxing through the primary constructor) needs full Kotlin reflection.
    implementation(kotlin("reflect"))

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation("com.h2database:h2")
    testImplementation("com.mysql:mysql-connector-j")
    testImplementation("org.postgresql:postgresql")
    testImplementation("org.testcontainers:testcontainers-mysql")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation(libs.micrometer.observation.test)

    kotlinCompilerPluginClasspath(projects.kueryClientCompiler)

    jmhImplementation(platform(libs.spring.boot.bom))
    jmhImplementation("com.h2database:h2")
}
