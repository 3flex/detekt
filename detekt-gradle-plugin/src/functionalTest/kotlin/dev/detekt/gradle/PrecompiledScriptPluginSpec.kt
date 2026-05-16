package dev.detekt.gradle

import dev.detekt.gradle.testkit.withResourceDir
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test

class PrecompiledScriptPluginSpec {
    @Test
    fun `detektMain succeeds on a project with Gradle precompiled script plugins`() {
        val result = GradleRunner.create()
            .withResourceDir("precompiled-script")
            .withPluginClasspath()
            .withArguments("detektMain")
            .build()

        assertThat(result.task(":detektMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.output).doesNotContain("NoDescriptorForDeclarationException")
        assertThat(result.output).doesNotContain("Failed to analyze declaration")
    }
}
