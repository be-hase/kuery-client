plugins {
    id("conventions.kotlin")
    id("conventions.ktlint")
    id("conventions.detekt")
    id("conventions.java17")
    id("conventions.maven-publish")
}

dependencies {
    optional(platform(libs.spring.boot.bom))
    optional(platform(libs.micrometer.bom))

    api(projects.kueryClientCore)

    api("org.springframework.data:spring-data-jdbc")

    testImplementation("org.testcontainers:mysql")
    testImplementation("com.mysql:mysql-connector-j")
    testImplementation("io.micrometer:micrometer-observation-test")
}
