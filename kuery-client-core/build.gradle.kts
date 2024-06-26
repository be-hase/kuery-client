plugins {
    id("conventions.preset.base")
    id("conventions.maven-publish")
    id("conventions.jmh")
}

description = "Kuery client's core module."

dependencies {
    api(libs.micrometer.core)
    compileOnly(libs.kotlin.coroutines.core)
    testImplementation(libs.kotlin.coroutines.core)
}
