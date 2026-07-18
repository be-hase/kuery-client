package dev.hsbrysk.kuery.gradle

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class KueryClientGradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) {
        val extension = target.extensions.create(EXTENSION_NAME, KueryClientExtension::class.java)
        extension.autoTrimIndent.convention(false)
    }

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val extension = kotlinCompilation.target.project.extensions.getByType(KueryClientExtension::class.java)
        return extension.autoTrimIndent
            .zip(extension.sqlSyntaxCheck.orElse("")) { autoTrimIndent, sqlSyntaxCheck ->
                // Only emit an option when it deviates from the compiler plugin's default, so a
                // build whose compiler-plugin artifact resolves to an older kuery-client-compiler
                // (which would reject the unknown option) keeps working as long as the feature is
                // not enabled.
                buildList {
                    if (autoTrimIndent) add(SubpluginOption("autoTrimIndent", "true"))
                    // Only the empty string is the absent-sentinel from orElse(""); any other
                    // value — including a whitespace-only one — must be valid or fail loudly.
                    // The validation runs when the compile task's options are evaluated (still
                    // before the compiler), with a clear message instead of a compiler-plugin
                    // error deep in the build output.
                    if (sqlSyntaxCheck.isNotEmpty()) {
                        val value = sqlSyntaxCheck.trim()
                        if (SUPPORTED_SQL_SYNTAX_CHECKS.none { it.equals(value, ignoreCase = true) }) {
                            throw InvalidUserDataException(
                                "kueryClient.sqlSyntaxCheck must be one of " +
                                    "${SUPPORTED_SQL_SYNTAX_CHECKS.joinToString(", ")}, but was '$sqlSyntaxCheck'",
                            )
                        }
                        add(SubpluginOption("sqlSyntaxCheck", value))
                    }
                }
            }
    }

    override fun getCompilerPluginId(): String = "dev.hsbrysk.kuery-client"

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = "dev.hsbrysk.kuery-client",
        artifactId = "kuery-client-compiler",
        version = BuildConfig.VERSION,
    )

    // kuery-client-core is a JVM-only library, so the compiler plugin is only meaningful
    // for JVM compilations. Without this restriction, in a multiplatform project the plugin
    // would also be injected into JS/Native/wasm compilations.
    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean =
        when (kotlinCompilation.target.platformType) {
            KotlinPlatformType.jvm, KotlinPlatformType.androidJvm -> true
            else -> false
        }

    companion object {
        private const val EXTENSION_NAME = "kueryClient"

        // The vocabulary accepted by sqlSyntaxCheck. This module cannot depend on
        // kuery-client-compiler (that would drag the Kotlin compiler onto the Gradle classpath),
        // so this mirrors the SqlSyntaxCheck enum there — a test pins the two together.
        internal val SUPPORTED_SQL_SYNTAX_CHECKS =
            listOf("generic", "ansi", "oracle", "mysql", "sqlserver", "mariadb", "postgresql", "h2")
    }
}
