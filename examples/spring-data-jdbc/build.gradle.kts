import io.gitlab.arturbosch.detekt.Detekt

plugins {
    id("conventions.preset.base")
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.kuery.client)
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

    detektPlugins(projects.kueryClientDetekt)
}

detekt {
    config.setFrom("${rootProject.rootDir}/examples/detekt.yml")
    disableDefaultRuleSets = true
}

// Since they are dependent within the same project, I am writing it this way. There is no need to imitate this.
tasks.withType<Detekt> {
    dependsOn(":${projects.kueryClientDetekt.name}:assemble")
}
