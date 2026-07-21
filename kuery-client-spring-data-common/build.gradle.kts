plugins {
    id("conventions.preset.base")
    id("conventions.public-api")
    id("conventions.maven-publish")
}

description = "Shared Spring Data support for Kuery Client implementations."

dependencies {
    api(projects.kueryClientCore)
    api(libs.spring.data.commons)

    // Value class mapping (boxing through the primary constructor) needs full Kotlin reflection.
    implementation(kotlin("reflect"))

    testImplementation(libs.assertk)
}
