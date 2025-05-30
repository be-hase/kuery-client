plugins {
    id("conventions.preset.base")
    id("dev.hsbrysk.kuery-client")
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
}

description = "Example of spring-data-r2dbc"

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("dev.hsbrysk.kuery-client:kuery-client-spring-data-r2dbc")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.asyncer:r2dbc-mysql")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    detektPlugins("dev.hsbrysk.kuery-client:kuery-client-detekt")
}

detekt {
    config.setFrom("${rootProject.rootDir}/detekt.yml")
    disableDefaultRuleSets = true
}
