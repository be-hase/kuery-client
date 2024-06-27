package dev.hsbrysk.kuery.gradle

import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class KueryClientGradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> =
        kotlinCompilation.target.project.provider { emptyList() }

    override fun getCompilerPluginId(): String = "dev.hsbrysk.kuery-client"

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = "dev.hsbrysk.kuery-client",
        artifactId = "kuery-client-compiler",
        version = BuildConfig.VERSION,
    )

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean =
        kotlinCompilation.target.project.plugins.hasPlugin(KueryClientGradlePlugin::class.java)
}
