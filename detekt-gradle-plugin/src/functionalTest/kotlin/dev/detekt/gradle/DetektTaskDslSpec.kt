package dev.detekt.gradle

import dev.detekt.detekt_gradle_plugin.BuildConfig
import dev.detekt.gradle.testkit.DslTestBuilder.Companion.kotlin
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * These tests genuinely require Gradle to resolve dependencies, so they keep using the TestKit "dry run".
 * Tests that only verify the translation of plugin configuration into detekt CLI arguments have been migrated to the
 * much faster [DetektArgumentsSpec], which uses Gradle's ProjectBuilder. See detekt/detekt#3778.
 */
class DetektTaskDslSpec {

    private val defaultDetektVersion = BuildConfig.DETEKT_VERSION

    @Nested
    inner class `with an additional plugin` {
        private val config = """
            dependencies {
               detektPlugins("dev.detekt:ktlint-wrapper:$defaultDetektVersion")
            }
        """.trimIndent()
        private val builder = kotlin().dryRun()
        private val gradleRunner = builder.withDetektConfig(config).build()
        private val result = gradleRunner.runTasks("dependencies", "--configuration", "detektPlugins")

        @Test
        fun `successfully checks dependencies`() {
            assertThat(result.task(":dependencies")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }

        @Test
        fun `adds the ktlint-wrapper lib to the project dependencies`() {
            assertThat(result.output).contains("dev.detekt:ktlint-wrapper:$defaultDetektVersion")
        }
    }

    @Nested
    inner class `with a custom tool version` {
        private val customVersion = "1.0.0.RC8"

        private val config = """
            detekt {
                toolVersion = "$customVersion"
            }
        """.trimIndent()
        private val builder = kotlin().dryRun()
        private val gradleRunner = builder.withDetektConfig(config).build()
        private val result = gradleRunner.runTasks("dependencies", "--offline", "--configuration", "detekt")

        @Test
        fun `successfully checks dependencies`() {
            assertThat(result.task(":dependencies")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }

        @Test
        fun `adds the custom detekt version to the dependencies`() {
            assertThat(result.output).contains("dev.detekt:detekt-cli:$customVersion")
        }
    }
}
