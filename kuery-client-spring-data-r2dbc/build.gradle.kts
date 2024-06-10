plugins {
    id("conventions.kotlin")
    id("conventions.ktlint")
    id("conventions.detekt")
    id("conventions.java17")
    id("conventions.maven-publish")
}

dependencies {
    api(projects.kueryClientCore)

    optional(platform(libs.kotlin.coroutines.bom))
    optional(platform(libs.spring.boot.bom))
    api("org.springframework.data:spring-data-r2dbc")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    api("io.projectreactor:reactor-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("org.testcontainers:mysql")
    testImplementation("com.mysql:mysql-connector-j")
    testImplementation("io.asyncer:r2dbc-mysql")
    testImplementation(libs.micrometer.observation.test)
}
