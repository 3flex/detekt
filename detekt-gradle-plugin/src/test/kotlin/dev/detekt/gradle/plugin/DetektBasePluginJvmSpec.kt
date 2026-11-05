package dev.detekt.gradle.plugin

import dev.detekt.gradle.Detekt
import dev.detekt.gradle.testkit.DslGradleRunner
import dev.detekt.gradle.testkit.ProjectLayout
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.repositories
import org.jetbrains.kotlin.gradle.plugin.KotlinBaseApiPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.junit.jupiter.api.Test

/**
 * Verifies that the base plugin generates the expected per-source-set detekt tasks for JVM projects, by inspecting the
 * tasks' CLI arguments on an in-memory project built with Gradle's ProjectBuilder.
 *
 * The Android and KMP variants of these tests require AGP and the Android SDK, so they keep using the Gradle TestKit
 * "dry run" in the functional `DetektBasePluginSpec`. See detekt/detekt#3778.
 */
class DetektBasePluginJvmSpec {

    @Test
    fun `generates source set tasks for JVM project`() {
        val gradleRunner = DslGradleRunner(
            projectLayout = ProjectLayout(
                numberOfSourceFilesInRootPerSourceDir = 1,
                srcDirs = listOf(
                    "src/main/kotlin",
                    "src/test/kotlin",
                ),
            ),
            buildFileName = "build.gradle.kts",
            projectScript = {
                apply<KotlinPluginWrapper>()
                apply<DetektPlugin>()
                repositories {
                    mavenCentral()
                }
            },
        ).also { it.setupProject() }

        val project = gradleRunner.buildProject()
        project.checkSourceSetTask("main")
        project.checkSourceSetTask("test")
    }

    @Test
    fun `generates source set tasks when multiple plugins of type KotlinBasePlugin are applied #8613`() {
        val gradleRunner = DslGradleRunner(
            projectLayout = ProjectLayout(
                numberOfSourceFilesInRootPerSourceDir = 1,
                srcDirs = listOf(
                    "src/main/kotlin",
                    "src/test/kotlin",
                ),
            ),
            buildFileName = "build.gradle.kts",
            projectScript = {
                apply<KotlinPluginWrapper>() // This plugin has type KotlinBasePlugin
                apply<KotlinBaseApiPlugin>() // This plugin also has type KotlinBasePlugin
                apply<DetektPlugin>()
                repositories {
                    mavenCentral()
                }
            },
        ).also { it.setupProject() }

        val project = gradleRunner.buildProject()
        project.checkSourceSetTask("main")
        project.checkSourceSetTask("test")
    }

    private fun Project.checkSourceSetTask(sourceSet: String) {
        val taskName = "detekt${sourceSet.replaceFirstChar { it.uppercase() }}SourceSet"
        val arguments = (tasks.getByPath(taskName) as Detekt).arguments.joinToString(" ")
        assertThat(arguments).containsPattern("""--input \S*[/\\]src[/\\]$sourceSet[/\\]kotlin""")
        assertThat(arguments).containsPattern("""--report checkstyle:\S*[/\\]${sourceSet}SourceSet.xml""")
        assertThat(arguments).containsPattern("""--report sarif:\S*[/\\]${sourceSet}SourceSet.sarif""")
    }
}
