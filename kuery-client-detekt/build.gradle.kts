plugins {
    id("conventions.kotlin")
    id("conventions.ktlint")
    id("conventions.detekt")
}

dependencies {
    implementation(libs.detekt.api)
    testImplementation(libs.detekt.test)
}
