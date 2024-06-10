plugins {
    id("conventions.kotlin")
    id("conventions.ktlint")
    id("conventions.detekt")
    id("conventions.maven-publish")
}

dependencies {
    optional(platform(libs.kotlin.coroutines.bom))
    optional(platform(libs.micrometer.bom))

    api("io.micrometer:micrometer-core")
    optional("org.jetbrains.kotlinx:kotlinx-coroutines-core")
}
