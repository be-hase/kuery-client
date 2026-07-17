package dev.hsbrysk.kuery.gradle

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import assertk.assertions.message
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
    fun `the plugin is applicable to JVM compilations`(platformType: KotlinPlatformType) {
        assertThat(plugin.isApplicable(compilation(platformType))).isTrue()
    }

    @ParameterizedTest
    @EnumSource(KotlinPlatformType::class, names = ["jvm", "androidJvm"], mode = EnumSource.Mode.EXCLUDE)
    fun `the plugin is not applicable to non-JVM compilations`(platformType: KotlinPlatformType) {
        assertThat(plugin.isApplicable(compilation(platformType))).isFalse()
    }

    @Test
    fun `no option is emitted at the default so older compiler plugins keep working`() {
        // given
        val project = ProjectBuilder.builder().build()
        plugin.apply(project)

        // when
        val options = plugin.applyToCompilation(compilation(project)).get()

        // then
        assertThat(options).isEmpty()
    }

    @Test
    fun `autoTrimIndent is passed through when enabled`() {
        // given
        val project = ProjectBuilder.builder().build()
        plugin.apply(project)
        project.extensions.getByType(KueryClientExtension::class.java).autoTrimIndent.set(true)

        // when
        val options = plugin.applyToCompilation(compilation(project)).get()

        // then
        assertThat(options.single().toEqualsString()).isEqualTo("autoTrimIndent=true")
    }

    @Test
    fun `sqlSyntaxCheck is passed through when set`() {
        // given
        val project = ProjectBuilder.builder().build()
        plugin.apply(project)
        project.extensions.getByType(KueryClientExtension::class.java).sqlSyntaxCheck.set("postgresql")

        // when
        val options = plugin.applyToCompilation(compilation(project)).get()

        // then
        assertThat(options.single().toEqualsString()).isEqualTo("sqlSyntaxCheck=postgresql")
    }

    @Test
    fun `autoTrimIndent and sqlSyntaxCheck are both emitted when both are set`() {
        // given
        val project = ProjectBuilder.builder().build()
        plugin.apply(project)
        val extension = project.extensions.getByType(KueryClientExtension::class.java)
        extension.autoTrimIndent.set(true)
        extension.sqlSyntaxCheck.set("mysql")

        // when
        val options = plugin.applyToCompilation(compilation(project)).get()

        // then
        assertThat(options.map { it.toEqualsString() })
            .isEqualTo(listOf("autoTrimIndent=true", "sqlSyntaxCheck=mysql"))
    }

    @Test
    fun `a mixed-case sqlSyntaxCheck value is accepted and passed through verbatim`() {
        // The compiler resolves the value case-insensitively, so the plugin must not reject a
        // valid value on case alone.
        // given
        val project = ProjectBuilder.builder().build()
        plugin.apply(project)
        project.extensions.getByType(KueryClientExtension::class.java).sqlSyntaxCheck.set("Generic")

        // when
        val options = plugin.applyToCompilation(compilation(project)).get()

        // then
        assertThat(options.single().toEqualsString()).isEqualTo("sqlSyntaxCheck=Generic")
    }

    @Test
    fun `a whitespace-only sqlSyntaxCheck value is rejected instead of silently disabling the check`() {
        // given
        val project = ProjectBuilder.builder().build()
        plugin.apply(project)
        project.extensions.getByType(KueryClientExtension::class.java).sqlSyntaxCheck.set("  ")

        // when & then
        assertFailure { plugin.applyToCompilation(compilation(project)).get() }
            .message()
            .isNotNull()
            .contains("kueryClient.sqlSyntaxCheck must be one of")
    }

    @Test
    fun `a padded sqlSyntaxCheck value is trimmed and passed through`() {
        // given
        val project = ProjectBuilder.builder().build()
        plugin.apply(project)
        project.extensions.getByType(KueryClientExtension::class.java).sqlSyntaxCheck.set(" mysql ")

        // when
        val options = plugin.applyToCompilation(compilation(project)).get()

        // then
        assertThat(options.single().toEqualsString()).isEqualTo("sqlSyntaxCheck=mysql")
    }

    @Test
    fun `an invalid sqlSyntaxCheck value is rejected with the supported values`() {
        // given
        val project = ProjectBuilder.builder().build()
        plugin.apply(project)
        project.extensions.getByType(KueryClientExtension::class.java).sqlSyntaxCheck.set("db2")

        // when & then
        assertFailure { plugin.applyToCompilation(compilation(project)).get() }
            .message()
            .isNotNull()
            .contains("must be one of generic, ansi, oracle, mysql, sqlserver, mariadb, postgresql, h2, but was 'db2'")
    }

    private fun compilation(platformType: KotlinPlatformType): KotlinCompilation<*> = mockk {
        every { target.platformType } returns platformType
    }

    private fun compilation(project: org.gradle.api.Project): KotlinCompilation<*> = mockk {
        every { target.project } returns project
    }

    private fun SubpluginOption.toEqualsString(): String = "$key=$value"
}
