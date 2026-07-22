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

// PoC only (not published): KMP build of the core DSL to verify the compiler plugin on Kotlin/Native.
include("kmp-poc")
include("kuery-client-compiler")
include("kuery-client-compiler:benchmark")
include("kuery-client-compiler:benchmark-auto-trim")
include("kuery-client-compiler:functional-test")
include("kuery-client-compiler:functional-test-auto-trim")
include("kuery-client-core")
include("kuery-client-gradle-plugin")
include("kuery-client-gradle-plugin:functional-test")
include("kuery-client-spring-data-common")
include("kuery-client-spring-data-jdbc")
include("kuery-client-spring-data-r2dbc")
include("kuery-client-spring-data-testing")
