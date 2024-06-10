plugins {
    id("conventions.kotlin")
    id("conventions.ktlint")
    id("conventions.detekt")
    id("conventions.maven-publish")
}

dependencies {
    optional(platform(libs.kotlin.coroutines.bom))
    optional("org.jetbrains.kotlinx:kotlinx-coroutines-core")
}
