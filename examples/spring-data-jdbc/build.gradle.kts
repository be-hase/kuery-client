import io.gitlab.arturbosch.detekt.Detekt

plugins {
    id("conventions.kotlin")
    id("conventions.ktlint")
    id("conventions.detekt")
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
}

description = "Example of spring-data-jdbc"

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation(projects.kueryClientSpringDataJdbc)
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("com.mysql:mysql-connector-j")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    kotlinCompilerPluginClasspath(projects.kueryClientCompiler)

    detektPlugins(projects.kueryClientDetekt)
}

detekt {
    config.setFrom("${rootProject.rootDir}/examples/detekt.yml")
    disableDefaultRuleSets = true
}

tasks.withType<Detekt> {
    dependsOn(":${projects.kueryClientDetekt.name}:assemble")
}
