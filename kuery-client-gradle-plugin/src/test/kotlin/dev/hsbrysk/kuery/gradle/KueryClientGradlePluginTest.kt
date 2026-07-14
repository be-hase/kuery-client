package dev.hsbrysk.kuery.gradle

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockk
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
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

    private fun compilation(platformType: KotlinPlatformType): KotlinCompilation<*> = mockk {
        every { target.platformType } returns platformType
    }
}
