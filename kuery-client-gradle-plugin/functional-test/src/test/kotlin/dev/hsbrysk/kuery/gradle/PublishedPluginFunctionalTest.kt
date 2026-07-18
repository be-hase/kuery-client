package dev.hsbrysk.kuery.gradle

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Exercises the plugin exactly like a user project does: the plugin is applied by id and resolved
 * from a build-local Maven repository (marker artifact -> plugin jar -> compiler artifact), NOT
 * injected via TestKit's withPluginClasspath. This covers the publishing wiring — plugin id,
 * artifact coordinates, BuildConfig.VERSION — that in-repo functional tests bypass.
 */
class PublishedPluginFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    private val repoUri = File(System.getProperty("kueryClient.repo")).toURI()
    private val version = System.getProperty("kueryClient.version")
    private val kotlinVersion = System.getProperty("kueryClient.kotlinVersion")

    @Test
    fun `string interpolation in a consumer project is converted into bind parameters`() {
        writeSettings()
        writeBuild(
            """
            plugins {
                kotlin("jvm") version "$kotlinVersion"
                id("dev.hsbrysk.kuery-client") version "$version"
                application
            }

            dependencies {
                implementation("dev.hsbrysk.kuery-client:kuery-client-core:$version")
            }

            application {
                mainClass = "MainKt"
            }
            """,
        )
        writeSource(
            "src/main/kotlin/Main.kt",
            """
            import dev.hsbrysk.kuery.core.Sql

            fun main() {
                val userId = 42
                val sql = Sql { +"SELECT * FROM users WHERE user_id = ${'$'}userId" }
                println("body=" + sql.body)
                println("params=" + sql.parameters.joinToString(",") { it.name + "=" + it.value })
            }
            """,
        )

        val output = runner("run", "--quiet").build().output

        assertThat(output).contains("body=SELECT * FROM users WHERE user_id = :p0")
        assertThat(output).contains("params=p0=42")
    }

    @Test
    fun `autoTrimIndent set via the kueryClient extension trims multi-line SQL`() {
        writeSettings()
        writeBuild(
            """
            plugins {
                kotlin("jvm") version "$kotlinVersion"
                id("dev.hsbrysk.kuery-client") version "$version"
                application
            }

            kueryClient {
                autoTrimIndent = true
            }

            dependencies {
                implementation("dev.hsbrysk.kuery-client:kuery-client-core:$version")
            }

            application {
                mainClass = "MainKt"
            }
            """,
        )
        writeSource(
            "src/main/kotlin/Main.kt",
            """
            import dev.hsbrysk.kuery.core.Sql

            fun main() {
                val sql = Sql {
                    +${"\"\"\""}
                        SELECT *
                        FROM users
                    ${"\"\"\""}
                }
                println("body=[" + sql.body + "]")
            }
            """,
        )

        val output = runner("run", "--quiet").build().output

        assertThat(output).contains("body=[SELECT *\nFROM users]")
    }

    @Test
    fun `sqlSyntaxCheck set via the kueryClient extension reports a warning for broken SQL`() {
        writeSettings()
        writeBuild(
            """
            plugins {
                kotlin("jvm") version "$kotlinVersion"
                id("dev.hsbrysk.kuery-client") version "$version"
            }

            kueryClient {
                sqlSyntaxCheck = "mysql"
            }

            dependencies {
                implementation("dev.hsbrysk.kuery-client:kuery-client-core:$version")
            }
            """,
        )
        writeSource(
            "src/main/kotlin/Main.kt",
            """
            import dev.hsbrysk.kuery.core.Sql

            fun broken() = Sql { +"SELECT * FORM users" }
            """,
        )

        val output = runner("compileKotlin").build().output

        assertThat(output).contains("The SQL assembled from this block failed to parse")
    }

    @Test
    fun `in a multiplatform project the compiler plugin is applied to the JVM compilation only`() {
        writeSettings()
        writeBuild(
            """
            plugins {
                kotlin("multiplatform") version "$kotlinVersion"
                id("dev.hsbrysk.kuery-client") version "$version"
            }

            kotlin {
                jvm()
                js {
                    nodejs()
                }
                sourceSets {
                    jvmMain {
                        dependencies {
                            implementation("dev.hsbrysk.kuery-client:kuery-client-core:$version")
                        }
                    }
                }
            }

            tasks.register("printPluginClasspaths") {
                val jvm = tasks.named("compileKotlinJvm", org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class)
                    .map { it.pluginClasspath.files.map(File::getName) }
                val js = tasks.named("compileKotlinJs", org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile::class)
                    .map { it.pluginClasspath.files.map(File::getName) }
                doLast {
                    println("jvmPluginClasspath=" + jvm.get())
                    println("jsPluginClasspath=" + js.get())
                }
            }
            """,
        )
        writeSource(
            "src/jvmMain/kotlin/Sample.kt",
            """
            import dev.hsbrysk.kuery.core.Sql

            fun sample() = Sql { +"SELECT 1" }
            """,
        )

        val output = runner("compileKotlinJvm", "compileKotlinJs", "printPluginClasspaths", "--quiet")
            .build()
            .output

        val jvmLine = output.lineSequence().single { it.startsWith("jvmPluginClasspath=") }
        val jsLine = output.lineSequence().single { it.startsWith("jsPluginClasspath=") }
        assertThat(jvmLine).contains("kuery-client-compiler")
        assertThat(jsLine).doesNotContain("kuery-client-compiler")
    }

    private fun runner(vararg arguments: String): GradleRunner = GradleRunner.create()
        .withProjectDir(projectDir)
        .withArguments(*arguments, "--stacktrace")

    // The dev.hsbrysk.kuery-client group (plugin marker included) is pinned to the build-local
    // repository via exclusiveContent: released versions of the same group exist on Maven Central
    // and the Plugin Portal, so a plain repository ordering could silently fall back to a
    // published artifact when a local publication is missing — the test must fail instead.
    // External dependencies (Kotlin plugin, stdlib, ...) still resolve from the public repositories.
    private fun writeSettings() {
        writeSource(
            "settings.gradle.kts",
            """
            pluginManagement {
                repositories {
                    exclusiveContent {
                        forRepository {
                            maven(url = uri("$repoUri"))
                        }
                        filter {
                            includeGroup("dev.hsbrysk.kuery-client")
                        }
                    }
                    mavenCentral()
                    gradlePluginPortal()
                }
            }

            dependencyResolutionManagement {
                @Suppress("UnstableApiUsage")
                repositories {
                    exclusiveContent {
                        forRepository {
                            maven(url = uri("$repoUri"))
                        }
                        filter {
                            includeGroup("dev.hsbrysk.kuery-client")
                        }
                    }
                    mavenCentral()
                }
            }

            rootProject.name = "consumer"
            """,
        )
    }

    private fun writeBuild(content: String) {
        writeSource("build.gradle.kts", content)
    }

    private fun writeSource(
        path: String,
        content: String,
    ) {
        val file = projectDir.resolve(path)
        file.parentFile.mkdirs()
        file.writeText(content.trimIndent())
    }
}
