plugins {
    // No version: the Kotlin Gradle plugin is already on the build classpath (root build).
    kotlin("jvm")
}

// PoC KSP processor: generates sqlx4k RowMapper objects from @Record-annotated data classes.
// Deliberately compiled with the repo-wide Java 17 toolchain so the KSP worker (which runs in
// the Gradle daemon's JVM) can load it on any daemon 17+ — unlike sqlx4k-codegen (class files 65.0).
description = "PoC KSP processor for @Record row mappers."

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-api:2.3.10")
}
