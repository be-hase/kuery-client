plugins {
    id("conventions.kotlin")
    id("conventions.ktlint")
    id("conventions.detekt")
}

dependencies {
    implementation(projects.kueryClientCore)

    implementation(platform(libs.kotlin.coroutines.bom))
    implementation(platform(libs.spring.boot.bom))
    implementation("org.springframework.data:spring-data-r2dbc")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("org.testcontainers:mysql")
//    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("com.mysql:mysql-connector-j")
    testImplementation("io.asyncer:r2dbc-mysql")
//    testImplementation("org.jetbrains.kotlin:kotlin-reflect")
}
