package conventions.preset

plugins {
    id("conventions.kotlin")
    id("conventions.ktlint")
    id("conventions.detekt")
}

group = "dev.hsbrysk.kuery-client"

val defaultVersion = "latest-SNAPSHOT"

version = providers.gradleProperty("publishVersion").orNull
    ?: providers.environmentVariable("PUBLISH_VERSION").orNull
        ?: defaultVersion
