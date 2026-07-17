package dev.hsbrysk.kuery.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class KueryClientGradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) {
        target.extensions.create(EXTENSION_NAME, KueryClientExtension::class.java)
            .autoTrimIndent.convention(false)
    }

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val extension = kotlinCompilation.target.project.extensions.getByType(KueryClientExtension::class.java)
        return extension.autoTrimIndent.map { enabled ->
            // Only emit the option when it deviates from the compiler plugin's default, so a
            // build whose compiler-plugin artifact resolves to an older kuery-client-compiler
            // (which would reject the unknown option) keeps working as long as the feature is
            // not enabled.
            if (enabled) {
                listOf(SubpluginOption("autoTrimIndent", "true"))
            } else {
                emptyList()
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
    }
}
