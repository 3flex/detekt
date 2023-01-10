package io.gitlab.arturbosch.detekt.internal

import com.android.build.api.artifact.MultipleArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Component
import com.android.build.api.variant.TestComponent
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.DetektPlugin
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.TaskProvider

internal class DetektAndroid(private val project: Project) {

    private val mainTaskProvider: TaskProvider<Task> by lazy {
        project.tasks.register("${DetektPlugin.DETEKT_TASK_NAME}Main") {
            it.group = "verification"
            it.description = "EXPERIMENTAL: Run detekt analysis for production classes across " +
                "all variants with type resolution"
        }
    }

    private val testTaskProvider: TaskProvider<Task> by lazy {
        project.tasks.register("${DetektPlugin.DETEKT_TASK_NAME}Test") {
            it.group = "verification"
            it.description = "EXPERIMENTAL: Run detekt analysis for test classes across " +
                "all variants with type resolution"
        }
    }

    private val mainBaselineTaskProvider: TaskProvider<Task> by lazy {
        project.tasks.register("${DetektPlugin.BASELINE_TASK_NAME}Main") {
            it.group = "verification"
            it.description = "EXPERIMENTAL: Creates detekt baseline files for production classes across " +
                "all variants with type resolution"
        }
    }

    private val testBaselineTaskProvider: TaskProvider<Task> by lazy {
        project.tasks.register("${DetektPlugin.BASELINE_TASK_NAME}Test") {
            it.group = "verification"
            it.description = "EXPERIMENTAL: Creates detekt baseline files for test classes across " +
                "all variants with type resolution"
        }
    }

    fun registerTasks(extension: DetektExtension) {
        project.extensions.findByType(AndroidComponentsExtension::class.java)?.let { componentExtension ->
            componentExtension.onVariants { variant ->
                if (variant.name in extension.ignoredVariants) return@onVariants

                variant.components
                    .filterNot { extension.matchesIgnoredConfiguration(it) }
                    .filter { it.sources.kotlin != null  }
                    .forEach { component ->
                        project.registerAndroidDetektTask(extension, component)
                            .also { provider ->
                                if (component is TestComponent) {
                                    testTaskProvider.configure { it.dependsOn(provider) }
                                } else {
                                    mainTaskProvider.configure { it.dependsOn(provider) }
                                }
                            }
                        project.registerAndroidCreateBaselineTask(extension, component)
                            .also { provider ->
                                if (component is TestComponent) {
                                    testBaselineTaskProvider.configure { it.dependsOn(provider) }
                                } else {
                                    mainBaselineTaskProvider.configure { it.dependsOn(provider) }
                                }
                            }
                    }
            }
        }
    }

    private fun DetektExtension.matchesIgnoredConfiguration(component: Component): Boolean =
            component.buildType in ignoredBuildTypes || component.flavorName in ignoredFlavors
}

internal fun Project.registerAndroidDetektTask(
    extension: DetektExtension,
    component: Component,
    taskName: String = DetektPlugin.DETEKT_TASK_NAME + component.name.capitalize(),
    extraInputSource: FileCollection? = null
): TaskProvider<Detekt> =
    registerDetektTask(taskName, extension) {
        setSource(component.sources.kotlin!!.all)
        extraInputSource?.let { source(it) }
        classpath.setFrom(
            component.compileClasspath,
            component.artifacts.getAll(MultipleArtifact.ALL_CLASSES_JARS),
            component.artifacts.getAll(MultipleArtifact.ALL_CLASSES_DIRS),
        )
        // If a baseline file is configured as input file, it must exist to be configured, otherwise the task fails.
        // We try to find the configured baseline or alternatively a specific variant matching this task.
        extension.baseline?.existingVariantOrBaseFile(component.name)?.let { baselineFile ->
            baseline.convention(layout.file(project.provider { baselineFile }))
        }
        setReportOutputConventions(reports, extension, component.name)
        description = "EXPERIMENTAL: Run detekt analysis for ${component.name} classes with type resolution"
    }

internal fun Project.registerAndroidCreateBaselineTask(
    extension: DetektExtension,
    component: Component,
    taskName: String = DetektPlugin.BASELINE_TASK_NAME + component.name.capitalize(),
    extraInputSource: FileCollection? = null
): TaskProvider<DetektCreateBaselineTask> =
    registerCreateBaselineTask(taskName, extension) {
        setSource(component.sources.kotlin!!.all)
        extraInputSource?.let { source(it) }
        classpath.setFrom(
            component.compileClasspath,
            component.artifacts.getAll(MultipleArtifact.ALL_CLASSES_JARS),
            component.artifacts.getAll(MultipleArtifact.ALL_CLASSES_DIRS),
        )
        val variantBaselineFile = extension.baseline?.addVariantName(component.name)
        baseline.convention(project.layout.file(project.provider { variantBaselineFile }))
        description = "EXPERIMENTAL: Creates detekt baseline for ${component.name} classes with type resolution"
    }
