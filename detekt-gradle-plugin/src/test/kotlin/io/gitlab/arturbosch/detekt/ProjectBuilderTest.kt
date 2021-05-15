package io.gitlab.arturbosch.detekt

import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import io.gitlab.arturbosch.detekt.invoke.CliArgument
import org.assertj.core.api.Assertions.assertThat
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.repositories
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.File

object ProjectBuilderTest : Spek({
    describe("project builder") {
        it("does the test") {
//            val gradleRunner = DslGradleRunner(
//                projectLayout = ProjectLayout(numberOfSourceFilesInRootPerSourceDir = 1),
//                buildFileName = "build.gradle",
//                baselineFiles = listOf("baseline.xml", "baseline-main.xml", "baseline-test.xml"),
//                mainBuildFileContent = "",
//            )
//            gradleRunner.setupProject()
//            gradleRunner.createJavaClass("AJavaClass")

            val project = ProjectBuilder.builder()
                .withProjectDir(File("/path/to/project"))
                .build()

            project.run {
                apply<KotlinPluginWrapper>()
                apply<DetektPlugin>()
                repositories {
                    mavenCentral()
                    mavenLocal()
                }
                configure<DetektExtension> {
                    allRules = true
                    reports.txt.enabled = false
                }
            }

            project.getTasksByName("detektMain", true) // trigger project evaluation

            val detektTask = project.tasks.getByPath("detektMain") as Detekt
            val argumentString = detektTask.arguments.flatMap(CliArgument::toArgument).joinToString(" ")

            assertThat(argumentString).contains("--report xml:")
            assertThat(argumentString).contains("--report sarif:")
            assertThat(argumentString).doesNotContain("--report txt:")
        }
    }
})
