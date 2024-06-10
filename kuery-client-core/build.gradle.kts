plugins {
    id("conventions.kotlin")
    id("conventions.ktlint")
    id("conventions.detekt")
    id("conventions.maven-publish")
}

dependencies {
    compileOnly(platform(libs.kotlin.coroutines.bom))
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core")
}
