pluginManagement {
    includeBuild("../build-logic")
    includeBuild("../")
    repositories {
        gradlePluginPortal()
        // mavenLocal()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "examples"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

plugins {
    // Apply the foojay-resolver plugin to allow automatic download of JDKs
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

include("spring-data-jdbc")
include("spring-data-r2dbc")

// Include the root project to use `dev.hsbrysk.kuery-client:*` modules
includeBuild("../")
