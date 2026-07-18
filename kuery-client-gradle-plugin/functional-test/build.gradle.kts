plugins {
    id("conventions.preset.base")
}

description = "Black-box functional tests that exercise the published Gradle plugin path."

dependencies {
    testImplementation(gradleTestKit())
}

// This module deliberately has NO project dependency on the plugin or the compiler: the tests
// consume the artifacts published to the build-local repository below, resolving them exactly
// like a user project would (plugin id -> marker artifact -> plugin jar -> compiler artifact).
val functionalTestRepo = rootProject.layout.buildDirectory.dir("functional-test-repo")

tasks.test {
    dependsOn(
        ":kuery-client-core:publishAllPublicationsToFunctionalTestRepository",
        ":kuery-client-compiler:publishAllPublicationsToFunctionalTestRepository",
        ":kuery-client-gradle-plugin:publishAllPublicationsToFunctionalTestRepository",
    )
    // Re-run whenever the published artifacts change (each publish writes a new snapshot).
    inputs.dir(functionalTestRepo).withPropertyName("functionalTestRepo")

    systemProperty("kueryClient.repo", functionalTestRepo.get().asFile.absolutePath)
    systemProperty("kueryClient.version", version.toString())
    systemProperty("kueryClient.kotlinVersion", libs.versions.kotlin.core.get())
}
