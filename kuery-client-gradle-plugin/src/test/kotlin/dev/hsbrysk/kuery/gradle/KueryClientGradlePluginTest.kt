package dev.hsbrysk.kuery.gradle

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockk
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class KueryClientGradlePluginTest {
    private val plugin = KueryClientGradlePlugin()

    @ParameterizedTest
    @EnumSource(KotlinPlatformType::class, names = ["jvm", "androidJvm"])
    fun `applicable to JVM compilations`(platformType: KotlinPlatformType) {
        assertThat(plugin.isApplicable(compilation(platformType))).isTrue()
    }

    @ParameterizedTest
    @EnumSource(KotlinPlatformType::class, names = ["jvm", "androidJvm"], mode = EnumSource.Mode.EXCLUDE)
    fun `not applicable to non-JVM compilations`(platformType: KotlinPlatformType) {
        assertThat(plugin.isApplicable(compilation(platformType))).isFalse()
    }

    @Test
    fun `autoTrimIndent defaults to false`() {
        val project = ProjectBuilder.builder().build()
        plugin.apply(project)

        val options = plugin.applyToCompilation(compilation(project)).get()
        assertThat(options.single().toEqualsString()).isEqualTo("autoTrimIndent=false")
    }

    @Test
    fun `autoTrimIndent is passed through when enabled`() {
        val project = ProjectBuilder.builder().build()
        plugin.apply(project)
        project.extensions.getByType(KueryClientExtension::class.java).autoTrimIndent.set(true)

        val options = plugin.applyToCompilation(compilation(project)).get()
        assertThat(options.single().toEqualsString()).isEqualTo("autoTrimIndent=true")
    }

    private fun compilation(platformType: KotlinPlatformType): KotlinCompilation<*> = mockk {
        every { target.platformType } returns platformType
    }

    private fun compilation(project: org.gradle.api.Project): KotlinCompilation<*> = mockk {
        every { target.project } returns project
    }

    private fun SubpluginOption.toEqualsString(): String = "$key=$value"
}
