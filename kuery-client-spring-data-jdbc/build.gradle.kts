plugins {
    id("conventions.kotlin")
    id("conventions.ktlint")
    id("conventions.detekt")
    id("conventions.java17")
    id("conventions.maven-publish")
}

dependencies {
    api(projects.kueryClientCore)

    optional(platform(libs.spring.boot.bom))
    api("org.springframework.data:spring-data-jdbc")

    testImplementation("org.testcontainers:mysql")
    testImplementation("com.mysql:mysql-connector-j")
}
