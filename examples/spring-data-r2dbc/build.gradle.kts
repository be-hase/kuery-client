plugins {
    id("conventions.kotlin")
    id("conventions.ktlint")
    id("conventions.detekt")
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
}

description = "Example of spring-data-r2dbc"

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation(projects.kueryClientSpringDataR2dbc)
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.asyncer:r2dbc-mysql")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    detektPlugins(projects.kueryClientDetekt)
}
