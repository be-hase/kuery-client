plugins {
    id("conventions.kotlin")
    id("conventions.ktlint")
    id("conventions.detekt")
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation("org.springframework.data:spring-data-jdbc")

    testImplementation("org.testcontainers:mysql")
    testImplementation("com.mysql:mysql-connector-j")
}
