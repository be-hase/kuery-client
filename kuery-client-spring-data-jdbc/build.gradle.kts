plugins {
    id("conventions.preset.base")
    id("conventions.public-api")
    id("conventions.maven-publish")
    id("conventions.jmh")
    id("conventions.kover")
}

description = "Kuery client implementation using spring-data-jdbc."

dependencies {
    api(projects.kueryClientCore)

    api(libs.spring.data.jdbc)

    implementation(projects.kueryClientSpringDataCommon)

    // The JDBC mapper adapter still invokes Kotlin primary constructors directly.
    implementation(kotlin("reflect"))

    testImplementation(projects.kueryClientSpringDataTesting)
    testImplementation(platform(libs.spring.boot.bom))
    testImplementation("com.h2database:h2")
    testImplementation("com.mysql:mysql-connector-j")
    testImplementation("org.postgresql:postgresql")
    testImplementation("org.testcontainers:testcontainers-mysql")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation(libs.micrometer.observation.test)
    testImplementation(libs.kotlin.coroutines.test)

    kotlinCompilerPluginClasspath(projects.kueryClientCompiler)

    jmhImplementation(platform(libs.spring.boot.bom))
    jmhImplementation("com.h2database:h2")
}
