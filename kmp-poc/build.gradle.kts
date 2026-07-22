import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    // No version: the Kotlin Gradle plugin is already on the build classpath (root build).
    kotlin("multiplatform")
    // Third PoC stage: sqlx4k-codegen generates RowMapper implementations from @Table entities.
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

// NOTE: the KSP2 worker runs in the Gradle daemon's JVM, so the daemon itself must be on
// Java 21+ (sqlx4k-codegen class files are 65.0). Run with e.g.
//   JAVA_HOME=~/.gradle/jdks/eclipse_adoptium-21-.../Contents/Home ./gradlew :kmp-poc:jvmTest
ksp {
    arg("dialect", "sqlite")
    arg("output-package", "dev.hsbrysk.kuery.sqlx4k.generated")
    arg("validate-sql-schema", "false")
}

dependencies {
    add("kspCommonMainMetadata", "io.github.smyrgeorge:sqlx4k-codegen:1.12.0")
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
