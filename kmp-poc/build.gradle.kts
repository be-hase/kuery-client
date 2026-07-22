plugins {
    // No version: the Kotlin Gradle plugin is already on the build classpath (root build).
    kotlin("multiplatform")
}

// PoC (not published, no conventions applied): verifies that
//   1. the core SQL DSL (copied into commonMain with the same FQNs) compiles as KMP common code, and
//   2. the kuery-client compiler plugin rewrites string interpolation on Kotlin/Native as well.
description = "PoC: core DSL as Kotlin Multiplatform + compiler plugin on Kotlin/Native."

kotlin {
    // 21, not the repo-wide 17: sqlx4k's JVM artifacts are built with a newer class-file
    // version than Java 17 (UnsupportedClassVersionError on 17 — a real finding of this PoC).
    jvmToolchain(21)
    jvm()
    macosArm64()

    sourceSets {
        commonMain.dependencies {
            // Second PoC stage: a minimal KueryClient subset backed by sqlx4k (SQLite).
            implementation("io.github.smyrgeorge:sqlx4k-sqlite:1.12.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlin.coroutines.test)
        }
    }
}

// Same wiring as kuery-client-compiler/functional-test, but KMP creates one plugin-classpath
// configuration per compilation (jvm/native x main/test) — put the compiler plugin on all of them.
configurations
    .matching {
        it.name.startsWith("kotlinCompilerPluginClasspath") ||
            it.name.startsWith("kotlinNativeCompilerPluginClasspath")
    }
    .configureEach {
        project.dependencies.add(name, project(":kuery-client-compiler"))
    }
