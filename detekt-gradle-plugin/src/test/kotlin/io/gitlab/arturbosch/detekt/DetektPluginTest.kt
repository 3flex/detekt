package io.gitlab.arturbosch.detekt

import io.gitlab.arturbosch.detekt.DslTestBuilder.Companion.custom
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.TaskOutcome
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object DetektPluginTest : Spek({
    describe("detekt plugin") {

        fun Task.dependencies() = taskDependencies.getDependencies(this)

        it("lazily adds detekt as a dependency of the `check` task") {
            val project = ProjectBuilder.builder().build()

            /* Ordering here is important - to prove lazily adding the dependency works the LifecycleBasePlugin must be
             * added to the project after the detekt plugin. */
            project.pluginManager.apply(DetektPlugin::class.java)
            project.pluginManager.apply(LifecycleBasePlugin::class.java)

            assertThat(project.tasks.getAt("check").dependencies().map { it.name }).contains("detekt")
        }

        it("configures detekt configuration") {
            val project = ProjectBuilder.builder().build()
            project.pluginManager.apply(DetektPlugin::class.java)

            val config = project.configurations.findByName("detekt")

            assertThat(config).isNotNull
            assertThat(config?.isVisible).isFalse()
            assertThat(config?.isTransitive).isTrue()
        }

        it("configures detekt task for each source set") {
            val project = ProjectBuilder.builder().build()
            project.pluginManager.apply(DetektPlugin::class.java)
            project.pluginManager.apply("org.jetbrains.kotlin.jvm")

            val sourceSets = project.convention.getPlugin(JavaPluginConvention::class.java).sourceSets

            sourceSets.create("other")

            //TODO add tests for detektMain and detektTest tasks
            val task = project.tasks.findByName("detektOther") as Detekt

            assertThat(task).isInstanceOf(Detekt::class.java)
            assertThat(task.description).isEqualTo("EXPERIMENTAL & SLOW: Run detekt analysis for other classes with type resolution")
            assertThat(task.reports.xml.destination).isEqualTo(project.file("build/reports/detekt/other.xml"))
            assertThat(task.reports.html.destination).isEqualTo(project.file("build/reports/detekt/other.html"))
        }

        it("avoids detekt task configuration when task not required") {
            val buildFile = """
            plugins {
                id("io.gitlab.arturbosch.detekt")
            }

            def configuredTasks = []
            tasks.configureEach {
                configuredTasks << it
            }

            gradle.buildFinished {
                def configuredTaskPaths = configuredTasks*.path

                assert configuredTaskPaths == [':help']
            }
            """.trimIndent()

            val builder = custom("build.gradle").withDetektConfig(buildFile)

            val gradleRunner = builder.build()
            gradleRunner.runTasksAndCheckResult("help") { result ->
                assertThat(result.task(":help")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            }
        }
    }
})
