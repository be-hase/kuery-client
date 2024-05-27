plugins {
    id("conventions.kotlin")
    id("conventions.ktlint")
    id("conventions.detekt")
}

dependencies {
    implementation("org.springframework.data:spring-data-r2dbc:3.3.0")
    implementation("org.springframework.data:spring-data-jdbc:3.3.0")
}
