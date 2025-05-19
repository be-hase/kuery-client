pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        // mavenLocal()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
        // mavenLocal()
    }
}

rootProject.name = "kuery-client"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

plugins {
    // Apply the foojay-resolver plugin to allow automatic download of JDKs
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include("kuery-client-compiler")
include("kuery-client-compiler:functional-test")
include("kuery-client-core")
include("kuery-client-detekt")
include("kuery-client-gradle-plugin")
include("kuery-client-spring-data-jdbc")
include("kuery-client-spring-data-r2dbc")

include("examples:spring-data-jdbc")
include("examples:spring-data-r2dbc")
