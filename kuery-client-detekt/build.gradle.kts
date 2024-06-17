plugins {
    id("conventions.kotlin")
    id("conventions.ktlint")
    id("conventions.detekt")
    id("conventions.maven-publish")
}

description = "Detekt custom rules provided by kuery client."

dependencies {
    implementation(libs.detekt.api)
    testImplementation(libs.detekt.test)
    testImplementation(projects.kueryClientCore)
}
