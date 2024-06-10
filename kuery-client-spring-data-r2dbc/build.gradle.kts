plugins {
    id("conventions.kotlin")
    id("conventions.ktlint")
    id("conventions.detekt")
    id("conventions.java17")
    id("conventions.maven-publish")
}

description = "Kuery client implementation using spring-data-r2dbc."

dependencies {
    api(projects.kueryClientCore)

    api(libs.spring.data.r2dbc)
    api(libs.kotlin.coroutines.core)
    api(libs.kotlin.coroutines.reactor)

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation("com.mysql:mysql-connector-j")
    testImplementation("io.asyncer:r2dbc-mysql")
    testImplementation("org.testcontainers:mysql")
    testImplementation(libs.kotlin.coroutines.test)
    testImplementation(libs.micrometer.observation.test)
}
