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
        val project = kotlinCompilation.target.project
        val extension = project.extensions.getByType(KueryClientExtension::class.java)
        return project.provider {
            listOf(SubpluginOption("autoTrimIndent", extension.autoTrimIndent.get().toString()))
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
