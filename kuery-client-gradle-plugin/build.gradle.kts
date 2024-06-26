plugins {
    id("conventions.preset.base")
    `java-gradle-plugin`
    signing
    alias(libs.plugins.plugin.publish)
    alias(libs.plugins.buildconfig)
}

description = "Gradle plugin for the Kuery client compiler."

dependencies {
    implementation(kotlin("gradle-plugin-api"))
}

buildConfig {
    buildConfigField("VERSION", project.version.toString())
}

@Suppress("UnstableApiUsage")
gradlePlugin {
    val kueryClient by plugins.creating {
        id = "dev.hsbrysk.kuery-client"
        displayName = "Gradle plugin for the Kuery client compiler"
        description = """
            To use Kuery client, you need to use the Kotlin compiler plugin.
            This is the Gradle plugin for configuring it.
        """.trimIndent()
        tags = listOf("kotlin", "kuery-client")
        implementationClass = "dev.hsbrysk.kuery.gradle.KueryClientGradlePlugin"
    }

    website = "https://github.com/be-hase/kuery-client"
    vcsUrl = "https://github.com/be-hase/kuery-client"
}

signing {
    if (project.version.toString().endsWith("-SNAPSHOT")) {
        isRequired = false
    }
    useInMemoryPgpKeys(
        providers.environmentVariable("SIGNING_PGP_KEY").orNull,
        providers.environmentVariable("SIGNING_PGP_PASSWORD").orNull,
    )
}
