plugins {
    id("conventions.kotlin")
    id("conventions.ktlint")
    id("conventions.detekt")
    id("conventions.maven-publish")
}

description = "Kuery client implementation using spring-data-jdbc."

dependencies {
    api(projects.kueryClientCore)

    api(libs.spring.data.jdbc)

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation("com.mysql:mysql-connector-j")
    testImplementation("org.testcontainers:mysql")
    testImplementation(libs.micrometer.observation.test)

    kotlinCompilerPluginClasspath(projects.kueryClientCompiler)
}
