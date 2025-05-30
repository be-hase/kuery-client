plugins {
    id("conventions.preset.base")
    id("dev.hsbrysk.kuery-client")
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
}

description = "Example of spring-data-jdbc"

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("dev.hsbrysk.kuery-client:kuery-client-spring-data-jdbc")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("com.mysql:mysql-connector-j")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    detektPlugins("dev.hsbrysk.kuery-client:kuery-client-detekt")
}

detekt {
    config.setFrom("${rootProject.rootDir}/detekt.yml")
    disableDefaultRuleSets = true
}
