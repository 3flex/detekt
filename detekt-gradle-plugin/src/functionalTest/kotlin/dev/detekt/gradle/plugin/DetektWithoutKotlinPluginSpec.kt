package dev.detekt.gradle.plugin

import dev.detekt.gradle.testkit.DslGradleRunner
import dev.detekt.gradle.testkit.ProjectLayout
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test

class DetektWithoutKotlinPluginSpec {

    @Test
    fun `warns when no Kotlin Gradle plugin is on the plugin classpath`() {
        // The default TestKit plugin classpath bundles the Kotlin Gradle plugin (and its API). Drop it so that the
        // Kotlin Gradle plugin types are absent, mirroring a build that applies detekt without any Kotlin plugin.
        val classpathWithoutKotlin = GradleRunner.create()
            .withPluginClasspath()
            .pluginClasspath
            .filterNot { it.name.contains("kotlin-gradle-plugin") }

        val gradleRunner = DslGradleRunner(
            projectLayout = ProjectLayout(numberOfSourceFilesInRootPerSourceDir = 0, srcDirs = emptyList()),
            buildFileName = "build.gradle.kts",
            mainBuildFileContent = """
                plugins {
                    id("dev.detekt")
                }

                repositories {
                    mavenCentral()
                }
            """.trimIndent(),
            customPluginClasspath = classpathWithoutKotlin,
        ).also {
            it.setupProject()
        }

        // ':help' configures the project (triggering the guard) without realizing any Detekt task.
        gradleRunner.runTasksAndCheckResult(":help") { result ->
            assertThat(result.output).contains(DetektBasePlugin.KOTLIN_GRADLE_PLUGIN_MISSING_WARNING)
        }
    }

    @Test
    fun `does not warn when a Kotlin Gradle plugin is on the plugin classpath`() {
        val gradleRunner = DslGradleRunner(
            projectLayout = ProjectLayout(numberOfSourceFilesInRootPerSourceDir = 0, srcDirs = emptyList()),
            buildFileName = "build.gradle.kts",
            mainBuildFileContent = """
                plugins {
                    id("dev.detekt")
                    kotlin("jvm")
                }

                repositories {
                    mavenCentral()
                }
            """.trimIndent(),
        ).also {
            it.setupProject()
        }

        gradleRunner.runTasksAndCheckResult(":help") { result ->
            assertThat(result.output).doesNotContain(DetektBasePlugin.KOTLIN_GRADLE_PLUGIN_MISSING_WARNING)
        }
    }
}
