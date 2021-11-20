package io.gitlab.arturbosch.detekt

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.util.Files.contentOf
import org.gradle.testkit.runner.GradleRunner
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.File

object JvmSpec : Spek({
    describe("Type resolution on JVM") {
        val projectDir = checkNotNull(javaClass.classLoader.getResource("jvm")?.file)
        val result = GradleRunner.create()
            .withProjectDir(File(projectDir))
            .withPluginClasspath()
            .withArguments("detektMain")
            .buildAndFail()

        assertThat(result.output).contains("Build failed with 2 weighted issues.")
        assertThat(result.output).contains("ExitOutsideMain - [kotlinExit]")
        assertThat(result.output).contains("ExitOutsideMain - [javaExit]")

        assertThat(File("$projectDir/build/reports/detekt/main.html")).isFile()
        assertThat(contentOf(File("$projectDir/build/reports/detekt/main.html"), "UTF-8")).contains("Total: 2")
        assertThat(File("$projectDir/build/reports/detekt/main.sarif")).isFile()
        assertThat(File("$projectDir/build/reports/detekt/main.txt")).isFile()
        assertThat(File("$projectDir/build/reports/detekt/main.xml")).isFile()
    }
})
