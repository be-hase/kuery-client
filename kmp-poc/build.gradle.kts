import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    // No version: the Kotlin Gradle plugin is already on the build classpath (root build).
    kotlin("multiplatform")
    // RowMapper generation from @Record entities (see :kmp-poc:codegen).
    // 2.3.10 is the KSP version sqlx4k itself pairs with Kotlin 2.4.10.
    id("com.google.devtools.ksp") version "2.3.10"
}

// PoC (not published, no conventions applied): verifies that
//   1. the core SQL DSL (copied into commonMain with the same FQNs) compiles as KMP common code,
//   2. the kuery-client compiler plugin rewrites string interpolation on Kotlin/Native as well, and
//   3. a minimal KueryClient subset runs end-to-end on sqlx4k (SQLite), including KSP-generated
//      row mappers.
description = "PoC: core DSL as Kotlin Multiplatform + compiler plugin on Kotlin/Native."

kotlin {
    // 21, not the repo-wide 17: sqlx4k's JVM artifacts are built with a newer class-file
    // version than Java 17 (UnsupportedClassVersionError on 17 — a real finding of this PoC).
    jvmToolchain(21)
    jvm()
    macosArm64()

    sourceSets {
        commonMain {
            dependencies {
                // Second PoC stage: a minimal KueryClient subset backed by sqlx4k (SQLite).
                implementation("io.github.smyrgeorge:sqlx4k-sqlite:1.12.0")
            }
            // sqlx4k-codegen emits into the metadata (common) KSP output; same wiring as
            // sqlx4k's own examples.
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlin.coroutines.test)
        }
    }
}

// Fourth PoC stage: our own @Record processor replaces sqlx4k-codegen — no table name, no @Id,
// mapper-only generation. It also removes the "Gradle daemon must run on Java 21+" constraint
// that sqlx4k-codegen (class files 65.0) imposed on the KSP2 worker: :kmp-poc:codegen is built
// with the repo-wide Java 17 toolchain. (The jvmTest *runtime* still needs 21 — see above.)
dependencies {
    add("kspCommonMainMetadata", project(":kmp-poc:codegen"))
}

// Every target compilation consumes the common KSP output, so order them after it.
tasks.withType<KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
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
