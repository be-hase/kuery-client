plugins {
    // No version: the Kotlin Gradle plugin is already on the build classpath (root build).
    kotlin("multiplatform")
}

// PoC (not published, no conventions applied): verifies that
//   1. the core SQL DSL (copied into commonMain with the same FQNs) compiles as KMP common code, and
//   2. the kuery-client compiler plugin rewrites string interpolation on Kotlin/Native as well.
description = "PoC: core DSL as Kotlin Multiplatform + compiler plugin on Kotlin/Native."

kotlin {
    jvmToolchain(17)
    jvm()
    macosArm64()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
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
