plugins {
    id("conventions.kotlin")
    id("conventions.ktlint")
    id("conventions.detekt")
    id("conventions.maven-publish")
}

description = "Kuery client's core module."

dependencies {
    api(libs.micrometer.core)
    compileOnly(libs.kotlin.coroutines.core)
    testImplementation(libs.kotlin.coroutines.core)
}
