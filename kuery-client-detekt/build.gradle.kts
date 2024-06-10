plugins {
    id("conventions.kotlin")
    id("conventions.ktlint")
    id("conventions.detekt")
    id("conventions.maven-publish")
}

dependencies {
    implementation(libs.detekt.api)
    testImplementation(libs.detekt.test)
}
